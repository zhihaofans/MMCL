package dev.mmcl.plugin.auth.offline

import dev.mmcl.api.capability.Account
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.settings.SettingItem
import dev.mmcl.api.settings.SettingsSection
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
    override val title: String
        get() = if (isChinese(localeProvider())) "离线账户" else OFFLINE_ACCOUNT_EN
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.AUTH

    /** `onLoad` 之前不读语言，先按英文兜底。 */
    private var localeProvider: () -> String = { ENGLISH }

    override fun onLoad(context: PluginContext) {
        localeProvider = { context.locale }
        context.services.register(
            AuthProvider::class,
            OfflineAuthProvider(
                localeProvider = { context.locale },
                configuredName = { context.settings.getString(PLAYER_NAME_KEY) ?: "Player" },
            ),
        )
        context.services.register(SettingsSection::class, OfflineAuthSettings())
        context.logger.info("离线账户插件已加载（player.name 缺省为 Player）")
    }

    private companion object {
        const val PLAYER_NAME_KEY = "player.name"
    }
}

/**
 * 离线会话实现。
 *
 * @param localeProvider 当前界面语言；展示名跟随启动器语言，缺省英文
 * @param configuredName 读取当前配置的玩家名（插件在构造时注入，便于测试）
 */
class OfflineAuthProvider(
    private val localeProvider: () -> String = { ENGLISH },
    private val configuredName: () -> String = { "Player" },
) : AuthProvider {
    override val id: String = "offline"
    override val displayName: String
        get() = if (isChinese(localeProvider())) "离线账户" else OFFLINE_ACCOUNT_EN

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

/** 英文界面下的账户名。 */
private const val OFFLINE_ACCOUNT_EN = "Offline account"

/** 缺省语言：`onLoad` 之前用英文兜底。 */
private const val ENGLISH = "en"

/** 界面语言是否为中文；插件展示名跟随 `PluginContext.locale`，不写死。 */
private fun isChinese(locale: String): Boolean = locale.lowercase().startsWith("zh")

/** 离线账户的设置项：玩家名（用于离线 UUID 与显示名）。 */
class OfflineAuthSettings : SettingsSection {
    override val id: String = "dev.mmcl.auth.offline"

    override fun title(locale: String): String = if (isChinese(locale)) "离线账户" else "Offline account"

    override fun description(locale: String): String =
        if (isChinese(locale)) {
            "离线模式下启动游戏使用的玩家名；同时决定离线 UUID。"
        } else {
            "Player name used when launching offline; it also determines the offline UUID."
        }

    override fun items(locale: String): List<SettingItem> =
        listOf(
            SettingItem.Text(
                key = PLAYER_NAME_KEY,
                title = if (isChinese(locale)) "玩家名" else "Player name",
                description = if (isChinese(locale)) "留空使用 Player" else "Leave blank to use Player",
                placeholder = "Player",
                defaultValue = "Player",
            ),
        )

    private companion object {
        const val PLAYER_NAME_KEY = "player.name"
    }
}
