package dev.mmcl.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.api.capability.JavaRuntimeProvider
import dev.mmcl.api.capability.LocalePack
import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.api.service.SettingsService
import dev.mmcl.api.settings.SettingsSection
import dev.mmcl.api.ui.LauncherTheme
import dev.mmcl.api.ui.LocalLauncherLocale
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.ui.UiWidget
import dev.mmcl.app.pages.DownloadsPage
import dev.mmcl.app.pages.HomePage
import dev.mmcl.app.pages.InstancesPage
import dev.mmcl.app.pages.PluginFolder
import dev.mmcl.app.pages.PluginsPage
import dev.mmcl.app.pages.SettingsPage
import dev.mmcl.app.shell.AppRoot
import dev.mmcl.app.shell.AppShell
import dev.mmcl.app.shell.SavedWindowState
import dev.mmcl.app.shell.WindowStateStore
import dev.mmcl.app.wizard.OnboardingWizard
import dev.mmcl.app.wizard.WIZARD_DONE_KEY
import dev.mmcl.app.appearance.AppearanceStore
import dev.mmcl.app.appearance.SkinLoader
import dev.mmcl.app.auth.activeAccount
import dev.mmcl.app.appearance.loadBackgroundBitmap
import dev.mmcl.app.i18n.LocaleController
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.app.appearance.loadFontFamily
import dev.mmcl.app.shell.applyUiScaleOverride
import dev.mmcl.app.shell.minimumWindowSize
import dev.mmcl.app.shell.toSavedWindowState
import dev.mmcl.app.theme.ThemeController
import dev.mmcl.core.builtin.BuiltinPluginSync
import dev.mmcl.core.platform.DirectoryLauncher
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
import dev.mmcl.core.java.JavaRuntimeInstaller
import dev.mmcl.core.java.JavaRuntimeSources
import dev.mmcl.core.launch.MinecraftLauncher
import dev.mmcl.core.net.JdkHttpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.mmcl.core.download.DownloadsService
import dev.mmcl.core.download.GameInstaller
import dev.mmcl.core.download.FileDownloader
import java.nio.file.Files
import org.slf4j.LoggerFactory

private const val SAFE_MODE_ARG = "--safe-mode"

/** macOS 透明标题栏左侧留给红黄绿按钮的宽度。 */
private val MAC_TITLE_BAR_INSET = 78.dp

/** 窗口几何写盘前的合并窗口（毫秒）。 */
private const val SAVE_DEBOUNCE_MILLIS = 300L
private const val SAFE_MODE_ENV = "MMCL_SAFE_MODE"

/**
 * 启动入口：准备数据目录 → 同步内置插件 → 加载插件 → 组装页面与主题 → 打开窗口。
 *
 * 安全模式（`--safe-mode` 或 `MMCL_SAFE_MODE=1`）会跳过全部插件主题与插件页面，
 * 用于"装了坏插件进不去界面"的自救（`UI_DESIGN.md` §5）。
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("dev.mmcl.app")
    // 必须在任何 AWT / Compose 初始化之前设置，Linux 分数缩放靠它兜底
    applyUiScaleOverride()?.let { scale -> logger.info("界面缩放覆盖：{} 倍（{}）", scale, "MMCL_UI_SCALE") }
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
    // 页面用的插件目录入口：装源插件、装运行时来源都要它
    val pluginFolder =
        PluginFolder(path = pluginsDir) { DirectoryLauncher(launcher.os).open(pluginsDir) }

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
    // 皮肤头像：地址由认证插件提供，这里只下载 + 缓存（<dataDir>/cache/skins）
    val skinLoader = SkinLoader(transport, launcher.dataDir.resolve("cache").resolve("skins"), logger)
    val detailService =
        VersionDetailService(
            versionsDir = launcher.dataDir.resolve("versions"),
            transport = transport,
            logger = logger,
            resolveUrl = { url -> manifestService.currentSource()?.resolveUrl(url) },
        )
    val instanceService =
        InstanceService(
            store = InstanceStore(launcher.dataDir.resolve("instances.json"), logger),
            instancesDir = launcher.dataDir.resolve("instances"),
            logger = logger,
        )
    val installer =
        GameInstaller(
            detailService = detailService,
            downloader =
                FileDownloader(
                    transport,
                    logger,
                    maxBytesPerSecond = {
                        (settings.getString(SettingsPage.LIMIT_KEY)?.toLongOrNull() ?: 0L) * 1024L
                    },
                ),
            root = launcher.dataDir,
            os = launcher.os,
            arch = launcher.arch,
            logger = logger,
            assetUrl = { hash -> manifestService.currentSource()?.assetObjectUrl(hash) },
            resolveUrl = { url -> manifestService.currentSource()?.resolveUrl(url) },
        )

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val downloads = DownloadsService(installer, appScope, logger)
    val intent = dev.mmcl.app.NavigationIntent()

    val pluginThemes: List<LauncherTheme> =
        if (safeMode) emptyList() else manager.services.getAll(LauncherTheme::class)
    val themeController = ThemeController(settings, pluginThemes)
    // 语言：内置 en / zh-CN，插件可注册额外语言包
    val localeController =
        LocaleController(
            settings = settings,
            packs = { manager.services.getAll(LocalePack::class) },
        )
    val strings = mutableStateOf(localeController.strings { key -> logger.debug("缺少文案 key：{}", key) })

    // 首次启动向导：完成/跳过后写 ui.onboarded；设置页可以重新打开
    val wizardVisible = mutableStateOf(settings.getString(WIZARD_DONE_KEY) != "true")
    if (wizardVisible.value) logger.info("首次启动向导待完成（设置页可重新打开，完成/跳过后写 {}）", WIZARD_DONE_KEY)
    val currentLocale = mutableStateOf(localeController.currentLocale())

    // 用户自选字体：设置页选完立即重排，不用重启
    val appearanceStore = AppearanceStore(settings)
    val customFontFamily = mutableStateOf(loadFontFamily(appearanceStore.load().fontFile))
    val customBackground = mutableStateOf(loadBackgroundBitmap(appearanceStore.load().backgroundImage))

    val javaDetector = JavaRuntimeDetector(launcher.dataDir, launcher.os, launcher.arch, settings, logger)
    // 来源插件可能随时被安装/卸载，这里每次求值；用户在设置里选中的来源优先
    val javaSources =
        JavaRuntimeSources(
            providers = { manager.services.getAll(JavaRuntimeProvider::class) },
            settings = settings,
        )
    val javaInstaller =
        JavaRuntimeInstaller(
            transport = transport,
            logger = logger,
            runtimeRoot = launcher.dataDir.resolve("runtime"),
            os = launcher.os,
            arch = launcher.arch,
        )
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
            HomePage(
                instances = instanceService,
                manifests = manifestService,
                authProviders = authProviders,
                activeProviderId = { settings.getString("auth.activeProvider") },
                playerName = settings.getString("player.name") ?: "Player",
                skinLoader = skinLoader,
                intent = intent,
                pluginFolder = pluginFolder,
            ),
            InstancesPage(
                manifests = manifestService,
                instanceService = instanceService,
                detailService = detailService,
                javaDetector = javaDetector,
                javaInstaller = javaInstaller,
                javaSources = javaSources,
                launcher = gameLauncher,
                launchContext = launchContext,
                authProviders = authProviders,
                activeProviderId = { settings.getString("auth.activeProvider") },
                intent = intent,
                autoLoad = true,
                pluginFolder = pluginFolder,
                downloads = downloads,
            ),
            DownloadsPage(
                manifests = manifestService,
                downloads = downloads,
                instanceService = instanceService,
                intent = intent,
                autoLoad = true,
                pluginFolder = pluginFolder,
            ),
            PluginsPage(plugins),
            SettingsPage(
                launcher,
                themeController,
                safeMode,
                manifestService,
                settings,
                javaSources,
                appearance = appearanceStore,
                onFontFamilyChanged = { family -> customFontFamily.value = family },
                onBackgroundChanged = { bitmap -> customBackground.value = bitmap },
                localeController = localeController,
                pluginSections = manager.services.getAll(SettingsSection::class),
                onLocaleChanged = { updated ->
                    strings.value = updated
                    currentLocale.value = localeController.currentLocale()
                },
                onOpenWizard = { wizardVisible.value = true },
                authProviders = authProviders,
            ),
        )
    val pluginPages: List<UiPage> =
        if (safeMode) emptyList() else manager.services.getAll(UiPage::class)
    val pluginWidgets: List<UiWidget> =
        if (safeMode) emptyList() else manager.services.getAll(UiWidget::class)
    val registry = UiRegistry(builtinPages, pluginPages, pluginWidgets, safeMode)
    val pages = registry.pages

    logger.info(
        "MMCL {} 就绪：页面 {} 个（插件页 {} 个）、主题 {} 个、插件 {} 个（启用 {} 个）",
        launcher.version,
        pages.size,
        pluginPages.size,
        themeController.all().size,
        plugins.size,
        plugins.count { it.state == PluginState.ENABLED },
    )

    // 窗口几何跨启动保留（UI_DESIGN.md P5）
    val windowStateStore = WindowStateStore(settings)
    val macChrome = launcher.os == OperatingSystem.MACOS

    application {
        val savedWindow = remember { windowStateStore.load() }
        val windowState =
            rememberWindowState(
                placement = if (savedWindow.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
                position =
                    if (savedWindow.x != null && savedWindow.y != null) {
                        WindowPosition(savedWindow.x.dp, savedWindow.y.dp)
                    } else {
                        WindowPosition(Alignment.Center)
                    },
                size = DpSize(savedWindow.width.dp, savedWindow.height.dp),
            )
        Window(
            onCloseRequest = {
                windowStateStore.save(windowState.toSavedWindowState())
                manager.close()
                exitApplication()
            },
            title = "MMCL ${launcher.version}",
            state = windowState,
        ) {
            // 最小尺寸随屏幕可用工作区变化：高缩小小屏上不能比屏幕还大
            val density = LocalDensity.current
            LaunchedEffect(Unit) {
                val minimum = minimumWindowSize()
                window.minimumSize =
                    java.awt.Dimension(
                        with(density) { minimum.width.roundToPx() },
                        with(density) { minimum.height.roundToPx() },
                    )
            }
            if (macChrome) {
                // 内容延伸到标题栏，只保留原生红黄绿；缩放 / 阴影 / 全屏仍由系统处理
                LaunchedEffect(Unit) {
                    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    window.rootPane.putClientProperty("apple.awt.draggableWindowBackground", true)
                }
            }
            LaunchedEffect(windowState) {
                // 拖动 / 缩放会连续触发，用 collectLatest + delay 合并成一次写盘
                var lastNormal: SavedWindowState? = null
                snapshotFlow { windowState.toSavedWindowState() }
                    .distinctUntilChanged()
                    .collectLatest { current ->
                        delay(SAVE_DEBOUNCE_MILLIS)
                        if (!current.maximized) lastNormal = current
                        // 最大化时窗口尺寸等于屏幕尺寸，不能覆盖还原后的大小
                        windowStateStore.save(
                            if (current.maximized) (lastNormal ?: current).copy(maximized = true) else current,
                        )
                    }
            }
            CompositionLocalProvider(
                LocalStrings provides strings.value,
                LocalLauncherLocale provides currentLocale.value,
            ) {
            AppRoot(
                themeController,
                fontFamily = customFontFamily.value,
                background = customBackground.value,
            ) {
                if (wizardVisible.value) {
                    OnboardingWizard(
                        manifests = manifestService,
                        downloads = downloads,
                        instanceService = instanceService,
                        javaSources = javaSources,
                        pluginFolder = pluginFolder,
                        onFinished = {
                            settings.putString(WIZARD_DONE_KEY, "true")
                            wizardVisible.value = false
                        },
                    )
                } else {
                    AppShell(
                        pages = pages,
                        launcher = launcher,
                        safeMode = safeMode,
                        pluginCount = plugins.size,
                        downloads = downloads,
                        widgets = registry.widgets,
                        titleBarInset = if (macChrome) MAC_TITLE_BAR_INSET else 0.dp,
                        accountName =
                            authProviders
                                .activeAccount(settings.getString("auth.activeProvider"))
                                ?.accountName,
                        accountSkinUrlProvider = {
                            authProviders
                                .activeAccount(settings.getString("auth.activeProvider"))
                                ?.skinUrl
                        },
                        skinLoader = skinLoader,
                    )
                }
            }
            }
        }
    }
}
