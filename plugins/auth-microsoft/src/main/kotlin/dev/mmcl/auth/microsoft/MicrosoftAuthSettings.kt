package dev.mmcl.auth.microsoft

import dev.mmcl.api.settings.SettingItem
import dev.mmcl.api.settings.SettingsSection

/**
 * 微软账号的插件设置区：把 client ID 放进「设置 → 插件设置」，和离线玩家名保持同一入口。
 *
 * 只声明设置项，读写由 `app` 负责（`SettingsService`）；插件本身不依赖 Compose。
 */
internal class MicrosoftAuthSettings : SettingsSection {
    override val id: String = "dev.mmcl.auth.microsoft"

    override fun title(locale: String): String =
        if (isChinese(locale)) "微软账号" else "Microsoft account"

    override fun description(locale: String): String =
        if (isChinese(locale)) {
            "Azure 应用（public client）的 Application (client) ID；需要开启「允许公共客户端流」，" +
                "并向 Mojang 提交 AppID 审核后才能真正登录。"
        } else {
            "Application (client) ID of your Azure public client. Enable public client flows and " +
                "submit the AppID to Mojang for approval before signing in."
        }

    override fun items(locale: String): List<SettingItem> =
        listOf(
            SettingItem.Text(
                key = MicrosoftAuthPlugin.CLIENT_ID_KEY,
                title = if (isChinese(locale)) "Client ID" else "Client ID",
                description =
                    if (isChinese(locale)) {
                        "在 Microsoft Entra → App registrations → Overview 里复制；留空则不启用微软登录。"
                    } else {
                        "Copy it from Microsoft Entra → App registrations → Overview. Leave blank to disable."
                    },
                placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                defaultValue = "",
            ),
        )
}

/** 界面语言是否为中文。 */
private fun isChinese(locale: String): Boolean = locale.lowercase().startsWith("zh")
