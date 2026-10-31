package dev.mmcl.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.api.capability.VersionManifestSource
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
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.app.pages.LaunchContext
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.core.instance.InstanceStore
import dev.mmcl.core.java.JavaRuntimeDetector
import dev.mmcl.core.launch.MinecraftLauncher
import dev.mmcl.core.net.JdkHttpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.mmcl.core.download.DownloadsService
import dev.mmcl.core.download.GameInstaller
import dev.mmcl.core.download.FileDownloader
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
    if (launcher.os == OperatingSystem.MACOS && launcher.arch != Architecture.ARM64) {
        logger.warn("不受支持的平台：macOS 只支持 Apple Silicon（当前 {}），Intel Mac 未适配", launcher.arch)
    }
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

    // 应用不内置任何下载源：清单源完全来自插件，没有源就只能启动已装好的实例
    val manifestService =
        VersionManifestService(
            cacheFile = launcher.dataDir.resolve("cache").resolve("version_manifest_v2.json"),
            logger = logger,
            sources = { manager.services.getAll(VersionManifestSource::class) },
            settings = settings,
        )

    if (manifestService.orderedSources().isEmpty()) {
        logger.warn("未安装下载源插件：本应用不内置下载源，无法获取游戏版本；把下载源插件放进 {} 后重启", pluginsDir)
    }

    val authProviders = manager.services.getAll(AuthProvider::class)
    if (authProviders.isEmpty()) {
        logger.warn("未安装认证插件：无法启动游戏（内置的 auth-offline 可被删除或禁用）")
    }

    val transport = JdkHttpTransport(userAgent = "MMCL/${launcher.version}")
    val detailService = VersionDetailService(launcher.dataDir.resolve("versions"), transport, logger)
    val instanceService =
        InstanceService(
            store = InstanceStore(launcher.dataDir.resolve("instances.json"), logger),
            instancesDir = launcher.dataDir.resolve("instances"),
            logger = logger,
        )
    val installer =
        GameInstaller(
            detailService = detailService,
            downloader = FileDownloader(transport, logger),
            root = launcher.dataDir,
            os = launcher.os,
            arch = launcher.arch,
            logger = logger,
            assetUrl = { hash -> manifestService.currentSource()?.assetObjectUrl(hash) },
        )

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val downloads = DownloadsService(installer, appScope, logger)
    val intent = dev.mmcl.app.NavigationIntent()

    val pluginThemes: List<LauncherTheme> =
        if (safeMode) emptyList() else manager.services.getAll(LauncherTheme::class)
    val themeController = ThemeController(settings, pluginThemes)

    val javaDetector = JavaRuntimeDetector(launcher.dataDir, launcher.os, launcher.arch, settings, logger)
    val gameLauncher = MinecraftLauncher(manager.events, logger, logFile = { launcher.dataDir.resolve("logs").resolve("latest.log") })
    val launchContext =
        LaunchContext(
            sharedRoot = launcher.dataDir,
            launcherVersion = launcher.version,
            os = launcher.os,
            arch = launcher.arch,
            playerName = settings.getString("player.name") ?: "Player",
        )

    val builtinPages: List<UiPage> =
        listOf(
            InstancesPage(
                manifests = manifestService,
                instanceService = instanceService,
                detailService = detailService,
                javaDetector = javaDetector,
                launcher = gameLauncher,
                launchContext = launchContext,
                authProviders = authProviders,
                intent = intent,
                autoLoad = true,
            ),
            DownloadsPage(
                manifests = manifestService,
                downloads = downloads,
                instanceService = instanceService,
                intent = intent,
                autoLoad = true,
            ),
            PluginsPage(plugins),
            SettingsPage(launcher, themeController, safeMode, manifestService),
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
                    downloads = downloads,
                )
            }
        }
    }
}
