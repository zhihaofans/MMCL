package dev.mmcl.plugin.update.checker

import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.ui.UiPage

/**
 * 更新检查插件（内置）：注册「更新」页，检查启动器与插件更新。
 *
 * 不调用 GitHub API：走 `releases/latest` 重定向 + Release 的 `update-index.json`，
 * 避免匿名 60 次/小时的 API 限额。
 */
class UpdateCheckerPlugin : Plugin {
    override val id: String = "dev.mmcl.update.checker"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val minLauncherVersion: String = "0.0.8"
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.TOOL

    override val title: String
        get() = if (localeProvider().lowercase().startsWith("zh")) "更新检查" else "Updater"

    private var localeProvider: () -> String = { "en" }

    override fun onLoad(context: PluginContext) {
        localeProvider = { context.locale }
        val checker =
            UpdateChecker(
                http = JdkUpdateHttp("MMCL-UpdateChecker/${context.launcher.version} (github.com/zhihaofans/MMCL)"),
                currentVersion = context.launcher.version,
                pluginsDir = context.launcher.dataDir.resolve("plugins"),
                logger = context.logger,
            )
        context.services.register(UiPage::class, UpdatePage(checker))
        context.logger.info("更新检查页已注册")
    }
}
