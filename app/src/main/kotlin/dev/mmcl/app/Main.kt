package dev.mmcl.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.mmcl.api.service.SettingsService
import dev.mmcl.api.ui.LauncherTheme
import dev.mmcl.api.ui.UiPage
import dev.mmcl.app.pages.DownloadsPage
import dev.mmcl.app.pages.InstancesPage
import dev.mmcl.app.pages.PluginsPage
import dev.mmcl.app.pages.SettingsPage
import dev.mmcl.app.shell.AppRoot
import dev.mmcl.app.shell.AppShell
import dev.mmcl.app.theme.ThemeController
import dev.mmcl.core.builtin.BuiltinPluginSync
import dev.mmcl.core.platform.Platforms
import dev.mmcl.core.plugin.PluginManager
import dev.mmcl.core.plugin.PluginState
import dev.mmcl.core.settings.FileSettingsService
import java.nio.file.Files
import org.slf4j.LoggerFactory

private const val SAFE_MODE_ARG = "--safe-mode"
private const val SAFE_MODE_ENV = "MMCL_SAFE_MODE"

/**
 * 启动入口：准备数据目录 → 同步内置插件 → 加载插件 → 组装页面与主题 → 打开窗口。
 *
 * 安全模式（`--safe-mode` 或 `MMCL_SAFE_MODE=1`）会跳过全部插件主题与插件页面，
 * 用于"装了坏插件进不去界面"的自救（`UI_DESIGN.md` §5）。
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("dev.mmcl.app")
    val safeMode = args.contains(SAFE_MODE_ARG) || System.getenv(SAFE_MODE_ENV)?.trim() == "1"
    if (safeMode) logger.warn("安全模式已开启：忽略全部插件主题与插件页面")

    val launcher = Platforms.launcherInfo(version = BuildInfo.version)
    Files.createDirectories(launcher.dataDir)
    val settings: SettingsService = FileSettingsService(launcher.dataDir.resolve("settings.properties"))

    val pluginsDir = launcher.dataDir.resolve("plugins")
    BuiltinPluginSync.sync(pluginsDir, logger)

    val manager = PluginManager(launcher, logger, settings = settings)
    val plugins =
        try {
            manager.load(pluginsDir)
        } catch (t: Throwable) {
            logger.error("插件加载失败，启动器继续以空插件列表运行", t)
            manager.plugins
        }

    val pluginThemes: List<LauncherTheme> =
        if (safeMode) emptyList() else manager.services.getAll(LauncherTheme::class)
    val themeController = ThemeController(settings, pluginThemes)

    val builtinPages: List<UiPage> =
        listOf(
            InstancesPage(),
            DownloadsPage(),
            PluginsPage(plugins),
            SettingsPage(launcher, themeController, safeMode),
        )
    val pluginPages: List<UiPage> =
        if (safeMode) emptyList() else manager.services.getAll(UiPage::class)
    val pages = UiRegistry(builtinPages, pluginPages, safeMode).pages

    logger.info(
        "MMCL {} 就绪：页面 {} 个（插件页 {} 个）、主题 {} 个、插件 {} 个（启用 {} 个）",
        launcher.version,
        pages.size,
        pluginPages.size,
        themeController.all().size,
        plugins.size,
        plugins.count { it.state == PluginState.ENABLED },
    )

    application {
        Window(
            onCloseRequest = {
                manager.close()
                exitApplication()
            },
            title = "MMCL ${launcher.version}",
        ) {
            AppRoot(themeController) {
                AppShell(
                    pages = pages,
                    launcher = launcher,
                    safeMode = safeMode,
                    pluginCount = plugins.size,
                )
            }
        }
    }
}
