package dev.mmcl.auth.microsoft

import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString

/** 微软 OAuth 端点。 */
internal object MicrosoftEndpoints {
    /** 设备码申请（`consumers` 租户：仅个人微软账号）。 */
    const val DEVICE_CODE: String = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"

    /** 令牌端点：设备码轮询与刷新都走这里。 */
    const val TOKEN: String = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

    /** 需要 Xbox Live 登录 + 刷新令牌。 */
    const val SCOPE: String = "XboxLive.signin offline_access"
}

/**
 * 设备码挑战：给用户看的 `userCode` 与浏览器地址。
 *
 * @property deviceCode 轮询用的内部凭据，不要展示给用户
 */
internal data class DeviceCodeChallenge(
    val userCode: String,
    val verificationUri: String,
    val deviceCode: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
) {
    /** 设备码何时过期（epoch 秒）。 */
    fun expiresAt(nowEpochSeconds: Long): Long = nowEpochSeconds + expiresInSeconds
}

/** 一次成功的 OAuth 令牌（[expiresAtEpochSeconds] 为本机时间）。 */
internal data class MicrosoftTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
)

/** 一次设备码轮询的结果。 */
internal sealed interface DeviceCodePoll {
    /** 用户已在浏览器确认，拿到令牌。 */
    data class Granted(val tokens: MicrosoftTokens) : DeviceCodePoll

    /** 用户还没确认；[slowDown] 表示服务端要求放慢轮询。 */
    data class Pending(val slowDown: Boolean) : DeviceCodePoll

    /** 登录失败，需要重新发起。 */
    data class Failed(val reason: MicrosoftAuthException.Reason, val detail: String?) : DeviceCodePoll
}

/**
 * 微软 OAuth 客户端（设备码流程）。
 *
 * @param clientId Azure 应用（public client，需开启「允许公共客户端流」）
 * @param transport HTTP 抽口，测试注入假实现
 * @param now 当前时间（epoch 秒），测试可注入
 * @param sleep 轮询间隔等待，测试注入后不真的睡
 */
internal class MicrosoftOAuthClient(
    private val clientId: String,
    private val transport: HttpTransport,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val sleep: suspend (Long) -> Unit = { seconds -> delay(seconds * 1000) },
) {
    /** 申请设备码。 */
    suspend fun requestDeviceCode(): DeviceCodeChallenge {
        val body =
            transport.postForm(
                MicrosoftEndpoints.DEVICE_CODE,
                mapOf("client_id" to clientId, "scope" to MicrosoftEndpoints.SCOPE),
            )
        val response = decode<DeviceCodeResponse>(body)
        return DeviceCodeChallenge(
            userCode = response.userCode,
            verificationUri = response.verificationUri,
            deviceCode = response.deviceCode,
            expiresInSeconds = response.expiresIn,
            intervalSeconds = response.interval,
        )
    }

    /** 轮询一次；调用方负责按 [DeviceCodeChallenge.intervalSeconds] 控制节奏。 */
    suspend fun poll(deviceCode: String): DeviceCodePoll {
        val body =
            transport.postForm(
                MicrosoftEndpoints.TOKEN,
                mapOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id" to clientId,
                    "device_code" to deviceCode,
                ),
            )
        val response = decode<TokenResponse>(body)
        response.error?.let { error ->
            return when (error) {
                "authorization_pending" -> DeviceCodePoll.Pending(slowDown = false)
                "slow_down" -> DeviceCodePoll.Pending(slowDown = true)
                "expired_token" -> DeviceCodePoll.Failed(MicrosoftAuthException.Reason.DEVICE_CODE_EXPIRED, body)
                "authorization_declined", "access_denied" ->
                    DeviceCodePoll.Failed(MicrosoftAuthException.Reason.AUTHORIZATION_DECLINED, body)
                else ->
                    DeviceCodePoll.Failed(
                        MicrosoftAuthException.Reason.OAUTH_ERROR,
                        "$error：${response.errorDescription.orEmpty()}",
                    )
            }
        }
        val accessToken =
            response.accessToken
                ?: throw MicrosoftAuthException(MicrosoftAuthException.Reason.INVALID_RESPONSE, "响应缺少 access_token")
        return DeviceCodePoll.Granted(
            MicrosoftTokens(
                accessToken = accessToken,
                refreshToken = response.refreshToken,
                expiresAtEpochSeconds = now() + (response.expiresIn ?: 3600),
            ),
        )
    }

    /**
     * 轮询直到用户确认、设备码过期或失败。
     *
     * @param deadlineEpochSeconds 设备码过期时间（epoch 秒）
     * @param onPending 每次轮询等待前回调，可用于更新 UI 的倒计时
     */
    suspend fun awaitDeviceCode(
        deviceCode: String,
        intervalSeconds: Long,
        deadlineEpochSeconds: Long,
        onPending: (Long) -> Unit = {},
    ): MicrosoftTokens {
        var interval = intervalSeconds.coerceAtLeast(1)
        while (now() < deadlineEpochSeconds) {
            onPending(interval)
            sleep(interval)
            when (val result = poll(deviceCode)) {
                is DeviceCodePoll.Granted -> return result.tokens
                is DeviceCodePoll.Pending -> if (result.slowDown) interval += 5
                is DeviceCodePoll.Failed -> throw MicrosoftAuthException(result.reason, result.detail)
            }
        }
        throw MicrosoftAuthException(MicrosoftAuthException.Reason.DEVICE_CODE_EXPIRED, "设备码已过期")
    }

    /** 用刷新令牌换新的访问令牌。 */
    suspend fun refresh(refreshToken: String): MicrosoftTokens {
        val body =
            transport.postForm(
                MicrosoftEndpoints.TOKEN,
                mapOf(
                    "grant_type" to "refresh_token",
                    "client_id" to clientId,
                    "refresh_token" to refreshToken,
                    "scope" to MicrosoftEndpoints.SCOPE,
                ),
            )
        val response = decode<TokenResponse>(body)
        response.error?.let { error ->
            throw MicrosoftAuthException(
                MicrosoftAuthException.Reason.NOT_SIGNED_IN,
                "$error：${response.errorDescription.orEmpty()}",
            )
        }
        val accessToken =
            response.accessToken
                ?: throw MicrosoftAuthException(MicrosoftAuthException.Reason.INVALID_RESPONSE, "响应缺少 access_token")
        return MicrosoftTokens(
            accessToken = accessToken,
            refreshToken = response.refreshToken,
            expiresAtEpochSeconds = now() + (response.expiresIn ?: 3600),
        )
    }

    private inline fun <reified T> decode(body: String): T =
        try {
            MicrosoftJson.decodeFromString<T>(body)
        } catch (e: IllegalArgumentException) {
            throw MicrosoftAuthException(MicrosoftAuthException.Reason.INVALID_RESPONSE, "响应不是合法 JSON", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw MicrosoftAuthException(MicrosoftAuthException.Reason.INVALID_RESPONSE, "响应结构不符合预期", e)
        }
}
