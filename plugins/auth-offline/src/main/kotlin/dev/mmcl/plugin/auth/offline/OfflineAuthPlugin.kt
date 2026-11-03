package dev.mmcl.plugin.auth.offline

import dev.mmcl.api.capability.Account
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import java.util.UUID

/**
 * 离线账户插件：把"离线占位"从 core 挪出来，通过 [AuthProvider] 提供。
 *
 * - 名字读设置 `player.name`，缺省 `Player`；
 * - UUID 用官方离线算法 `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`；
 * - token 固定 `0`，`userType` 为 `legacy`（离线不能进正版服务器）。
 */
class OfflineAuthPlugin : Plugin {
    override val id: String = "dev.mmcl.auth.offline"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT

    override fun onLoad(context: PluginContext) {
        context.services.register(
            AuthProvider::class,
            OfflineAuthProvider { context.settings.getString(PLAYER_NAME_KEY) ?: "Player" },
        )
        context.logger.info("离线账户插件已加载（player.name 缺省为 Player）")
    }

    private companion object {
        const val PLAYER_NAME_KEY = "player.name"
    }
}

/**
 * 离线会话实现。
 *
 * @param configuredName 读取当前配置的玩家名（插件在构造时注入，便于测试）
 */
class OfflineAuthProvider(
    private val configuredName: () -> String = { "Player" },
) : AuthProvider {
    override val id: String = "offline"
    override val displayName: String = "离线账户"

    /** 离线实现永远让位于真实认证插件。 */
    override val priority: Int = 1000

    override suspend fun login(): Account = createSession(configuredName())

    override suspend fun createSession(username: String): Account {
        val name = username.trim().ifBlank { "Player" }
        return Account(
            uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)).toString(),
            name = name,
            accessToken = "0",
            userType = "legacy",
        )
    }
}
