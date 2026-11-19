package dev.mmcl.auth.microsoft

import dev.mmcl.api.capability.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.Logger

/** 账户页展示的登录状态。 */
internal sealed interface LoginState {
    /** 未登录，可以开始登录。 */
    data object Idle : LoginState

    /** 已在浏览器里显示了设备码，等待用户确认。 */
    data class WaitingCode(val challenge: DeviceCodeChallenge) : LoginState

    /** 用户已确认，正在换 Minecraft 会话。 */
    data object Exchanging : LoginState

    /** 已登录。 */
    data class SignedIn(val account: Account) : LoginState

    /** 失败；页面按 [reason] 给出可操作的提示。 */
    data class Failed(val reason: MicrosoftAuthException.Reason, val detail: String?) : LoginState
}

/**
 * 登录状态机，持有在插件里而不是页面里。
 *
 * 页面切走时 Compose 的 `remember` 会被清掉；把任务挂在插件作用域上，
 * 用户去 Downloads 页再回来仍然能看到进度（这是 `AGENTS.md` §9 记过的坑）。
 */
internal class MicrosoftLoginService(
    private val provider: MicrosoftAuthProvider,
    private val oauthFactory: () -> MicrosoftOAuthClient?,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val _state = MutableStateFlow<LoginState>(initialState())

    /** 当前登录状态；页面用 `collectAsState()` 订阅。 */
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private var job: Job? = null

    /** 开始设备码登录；重复调用在已有任务时忽略。 */
    fun beginLogin() {
        if (job?.isActive == true) return
        val client =
            oauthFactory()
                ?: run {
                    _state.value = LoginState.Failed(MicrosoftAuthException.Reason.NO_CLIENT_ID, null)
                    return
                }
        job =
            scope.launch {
                try {
                    val challenge = client.requestDeviceCode()
                    _state.value = LoginState.WaitingCode(challenge)
                    val tokens =
                        client.awaitDeviceCode(
                            deviceCode = challenge.deviceCode,
                            intervalSeconds = challenge.intervalSeconds,
                            deadlineEpochSeconds = challenge.expiresAt(now()),
                        )
                    _state.value = LoginState.Exchanging
                    val account = provider.completeLogin(tokens)
                    _state.value = LoginState.SignedIn(account)
                } catch (e: CancellationException) {
                    _state.value = initialState()
                    throw e
                } catch (e: MicrosoftAuthException) {
                    logger.warn("微软登录失败：{}（{}）", e.reason, e.message, e)
                    _state.value = LoginState.Failed(e.reason, e.message)
                } catch (e: Exception) {
                    logger.error("微软登录失败，已隔离", e)
                    _state.value = LoginState.Failed(MicrosoftAuthException.Reason.HTTP_ERROR, e.message)
                }
            }
    }

    /** 取消进行中的登录并回到未登录状态。 */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = initialState()
    }

    /** 退出登录：清本地令牌并把状态复位。 */
    fun signOut() {
        job?.cancel()
        job = null
        provider.signOut()
        _state.value = LoginState.Idle
    }

    /** 页面重新进入时用当前本地登录态刷新一次（避免显示过期状态）。 */
    fun refresh() {
        if (job?.isActive == true) return
        _state.value = initialState()
    }

    private fun initialState(): LoginState =
        provider.accountOrNull()?.let { LoginState.SignedIn(it) } ?: LoginState.Idle
}
