package dev.mmcl.plugin.update.checker

import java.util.Locale

/** 更新页的极简双语文案（插件不依赖启动器内置 strings）。 */
internal object UpdateTexts {
    private val en =
        mapOf(
            "page.title" to "Updates",
            "current" to "Current version: %1\$s",
            "check" to "Check for updates",
            "checking" to "Checking…",
            "latest" to "Latest version: %1\$s",
            "up_to_date" to "You are up to date.",
            "launcher_update" to "Launcher update available: %1\$s → %2\$s",
            "open_release" to "Open release page",
            "plugins" to "Plugins",
            "plugins.empty" to "No updatable plugins in the release index.",
            "plugins.index_missing" to "This release has no update index. Download the latest launcher or open the release page.",
            "plugin.update" to "Update",
            "plugin.install" to "Install",
            "plugin.up_to_date" to "Up to date",
            "plugin.installed" to "Installed %1\$s, restart to load it.",
            "plugin.failed" to "Update failed: %1\$s",
            "update.failed" to "Update check failed: %1\$s",
            "builtin.note" to "Built-in plugins update together with the launcher.",
        )

    private val zh =
        mapOf(
            "page.title" to "更新",
            "current" to "当前版本：%1\$s",
            "check" to "检查更新",
            "checking" to "正在检查…",
            "latest" to "最新版本：%1\$s",
            "up_to_date" to "已是最新版本。",
            "launcher_update" to "启动器有新版本：%1\$s → %2\$s",
            "open_release" to "打开发布页",
            "plugins" to "插件",
            "plugins.empty" to "发布索引里没有可更新的插件。",
            "plugins.index_missing" to "这个版本没有更新索引；请下载最新启动器或打开发布页。",
            "plugin.update" to "更新",
            "plugin.install" to "安装",
            "plugin.up_to_date" to "已是最新",
            "plugin.installed" to "已安装 %1\$s，重启后生效。",
            "plugin.failed" to "更新失败：%1\$s",
            "update.failed" to "检查更新失败：%1\$s",
            "builtin.note" to "内置插件随启动器一起更新。",
        )

    fun t(locale: String, key: String, vararg args: Any): String {
        val table = if (locale.lowercase(Locale.ROOT).startsWith("zh")) zh else en
        val template = table[key] ?: en[key] ?: key
        return if (args.isEmpty()) template else String.format(Locale.ROOT, template, *args)
    }
}
