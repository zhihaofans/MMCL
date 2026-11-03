package dev.mmcl.auth.microsoft

import dev.mmcl.api.capability.Account
import dev.mmcl.api.capability.AuthProvider
import org.slf4j.Logger

/**
 * 微软账号认证：登录一次后长期有效，启动前静默刷新。
 *
 * 与离线账户的分工：本 provider 的 `priority` 更小（100 < 1000），
 * 但只有在**登录过**（本地有刷新令牌）时 `isSignedIn` 才为 true，
 * 否则启动器会继续用离线账户（见 `AuthProvider.isSignedIn`）。
 *
 * @param oauth 设备码/刷新客户端工厂；未配置 client id 时返回 `null`
 */
internal class MicrosoftAuthProvider(
    private val tokenStore: TokenStore,
    private val oauth: () -> MicrosoftOAuthClient?,
    private val xbox: XboxLiveClient,
    private val logger: Logger,
    private val localeProvider: () -> String = { "en" },
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) : AuthProvider {
    override val id: String = "microsoft"

    override val displayName: String
        get() = if (localeProvider().lowercase().startsWith("zh")) "微软账号" else "Microsoft account"

    override val priority: Int = 100

    /**
     * 有刷新令牌就算已登录。
     *
     * 不要求 Minecraft 令牌还没过期：令牌通常只有 24 小时，过期很正常，
     * 启动时 [createSession] 会自动刷新。真正的失效（刷新被拒）在启动那一刻报错。
     */
    override val isSignedIn: Boolean
        get() = tokenStore.load() != null

    override suspend fun login(): Account = session()

    override suspend fun createSession(username: String): Account = session()

    /** 当前登录的账户（不触发网络请求）；未登录返回 `null`。 */
    fun accountOrNull(): Account? {
        val stored = tokenStore.load() ?: return null
        val uuid = stored.uuid ?: return null
        val name = stored.name ?: return null
        return Account(uuid = uuid, name = name, accessToken = stored.accessToken.orEmpty(), userType = USER_TYPE)
    }

    /**
     * 设备码流程拿到微软令牌后调用：换 Minecraft 会话并落盘。
     *
     * @param tokens 设备码轮询得到的微软令牌
     */
    suspend fun completeLogin(tokens: MicrosoftTokens): Account {
        val stored = tokenStore.load()
        val refreshToken =
            tokens.refreshToken ?: stored?.refreshToken
                ?: throw MicrosoftAuthException(MicrosoftAuthException.Reason.NOT_SIGNED_IN, "响应里没有刷新令牌")
        return exchangeAndSave(refreshToken, tokens.accessToken)
    }

    /** 退出登录：只删本地令牌，不调用微软的撤销接口（失败也不该挡住退出）。 */
    fun signOut() {
        tokenStore.clear()
        logger.info("已退出微软账号")
    }

    /** 用本地刷新令牌换一次可用会话；没有登录态时抛 `NOT_SIGNED_IN`。 */
    suspend fun session(): Account {
        val stored =
            tokenStore.load()
                ?: throw MicrosoftAuthException(MicrosoftAuthException.Reason.NOT_SIGNED_IN, "尚未登录微软账号")

        val validToken = stored.accessToken
        if (validToken != null && stored.accessTokenExpiresAt - now() > EXPIRY_MARGIN_SECONDS) {
            val uuid = stored.uuid
            val name = stored.name
            if (uuid != null && name != null) {
                return Account(uuid = uuid, name = name, accessToken = validToken, userType = USER_TYPE)
            }
        }

        val client =
            oauth()
                ?: throw MicrosoftAuthException(MicrosoftAuthException.Reason.NO_CLIENT_ID, "插件设置里没有填写 client id")
        logger.info("Minecraft 令牌已过期，正在刷新")
        val refreshed =
            try {
                client.refresh(stored.refreshToken)
            } catch (e: MicrosoftAuthException) {
                // 刷新被拒通常意味着用户改了密码或撤销了授权：清掉本地登录态，别让启动一直失败
                if (e.reason == MicrosoftAuthException.Reason.NOT_SIGNED_IN) tokenStore.clear()
                throw e
            }
        return exchangeAndSave(
            refreshToken = refreshed.refreshToken ?: stored.refreshToken,
            microsoftAccessToken = refreshed.accessToken,
            expectedUuid = stored.uuid,
        )
    }

    /**
     * 换 Minecraft 会话并落盘。
     *
     * @param expectedUuid 刷新场景下本地保存的 UUID；换出来的账号不一致时说明
     *   刷新令牌已被换绑或串号，必须让用户重新授权，否则会把别人的账号当成自己的
     */
    private suspend fun exchangeAndSave(
        refreshToken: String,
        microsoftAccessToken: String,
        expectedUuid: String? = null,
    ): Account {
        val session = xbox.exchange(microsoftAccessToken)
        if (expectedUuid != null && !expectedUuid.equals(session.uuid, ignoreCase = true)) {
            tokenStore.clear()
            throw MicrosoftAuthException(
                MicrosoftAuthException.Reason.ACCOUNT_MISMATCH,
                "刷新后的账号与本地保存的不一致，请重新登录",
            )
        }
        tokenStore.save(
            StoredTokens(
                refreshToken = refreshToken,
                accessToken = session.accessToken,
                accessTokenExpiresAt = session.expiresAtEpochSeconds,
                uuid = session.uuid,
                name = session.name,
            ),
        )
        logger.info("微软账号已登录：{}", session.name)
        return Account(
            uuid = session.uuid,
            name = session.name,
            accessToken = session.accessToken,
            userType = USER_TYPE,
        )
    }

    private companion object {
        /** 启动时留出余量，避免"刚好过期"被游戏拒绝。 */
        const val EXPIRY_MARGIN_SECONDS = 60L
        const val USER_TYPE = "msa"
    }
}
