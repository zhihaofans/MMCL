package dev.mmcl.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.LauncherTheme
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.app.pages.DownloadsPage
import dev.mmcl.app.pages.GamePrepCard
import dev.mmcl.app.pages.InstancesPage
import dev.mmcl.app.pages.ManifestSummary
import dev.mmcl.app.pages.PluginsPage
import dev.mmcl.app.pages.SettingsPage
import dev.mmcl.app.theme.BuiltinThemes
import dev.mmcl.app.theme.ThemeController
import dev.mmcl.core.download.DownloadProgress
import dev.mmcl.core.download.FileDownloader
import dev.mmcl.core.download.GameInstaller
import dev.mmcl.core.download.InstallPlan
import dev.mmcl.core.download.PlannedFile
import dev.mmcl.core.net.HttpTransport
import dev.mmcl.core.plugin.LoadedPlugin
import dev.mmcl.core.plugin.PluginDescriptor
import dev.mmcl.core.plugin.PluginState
import dev.mmcl.core.settings.InMemorySettingsService
import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.core.version.LatestVersions
import dev.mmcl.core.version.VersionManifest
import dev.mmcl.core.version.VersionDetail
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.core.version.VersionManifestEntry
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.ui.kit.MmclPalettes
import dev.mmcl.ui.kit.PageScaffold
import dev.mmcl.ui.kit.SimpleLauncherTheme
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 无头渲染测试：用 `ImageComposeScene` 把外壳画成 PNG，不需要真实显示器。
 *
 * 产物落在 `app/build/screenshots/`，用于人工检查布局，也避免"编译过了但界面是空的"。
 */
@OptIn(ExperimentalComposeUiApi::class)
class AppShellRenderTest {
    private val screenshots: Path = Path.of("build", "screenshots")

    private val launcher =
        LauncherInfo(
            name = "MMCL",
            version = "0.0.1",
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
            dataDir = Path.of("/Users/demo/Library/Application Support/MMCL"),
        )

    private fun plugin(id: String) =
        LoadedPlugin(
            descriptor =
                PluginDescriptor(
                    id = id,
                    version = "0.0.1",
                    plugin = object : Plugin {
                        override val id: String = id
                        override val version: String = "0.0.1"
                    },
                ),
            state = PluginState.ENABLED,
        )

    private val plugins =
        listOf(
            plugin("dev.mmcl.plugin.hello"),
            plugin("dev.mmcl.ui.about.plugin"),
            plugin("dev.mmcl.theme.sakura.plugin"),
        )

    private val stubSource =
        object : VersionManifestSource {
            override val id: String = "mojang"
            override val displayName: String = "Mojang 官方"

            override suspend fun fetchManifestJson(): String = "{}"
        }

    private val manifestStub =
        VersionManifestService(
            cacheFile = Path.of("build", "tmp", "render-manifest.json"),
            logger = org.slf4j.LoggerFactory.getLogger("test.render"),
            sources = { listOf(stubSource) },
        )

    private val transportStub =
        object : HttpTransport {
            override suspend fun getText(url: String): String = "{}"

            override suspend fun downloadTo(url: String, target: Path) = Unit
        }

    private val installerStub =
        GameInstaller(
            detailService = VersionDetailService(Path.of("build", "tmp", "render-versions"), transportStub, org.slf4j.LoggerFactory.getLogger("test.render")),
            downloader = FileDownloader(transportStub, org.slf4j.LoggerFactory.getLogger("test.render"), concurrency = 1),
            root = Path.of("build", "tmp", "render-root"),
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
            logger = org.slf4j.LoggerFactory.getLogger("test.render"),
        )

    private val samplePlan =
        InstallPlan(
            detail = VersionDetail(id = "1.21.8", mainClass = "net.minecraft.client.main.Main"),
            files =
                listOf(
                    PlannedFile("versions/1.21.8/1.21.8.jar", "https://example.invalid/client.jar", "a", 26_000_000),
                    PlannedFile("libraries/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar", "https://example.invalid/lib.jar", "b", 90_000),
                ),
            warnings = emptyList(),
        )

    private val noSourceService =
        VersionManifestService(
            cacheFile = Path.of("build", "tmp", "render-manifest-empty.json"),
            logger = org.slf4j.LoggerFactory.getLogger("test.render"),
        )

    private val sampleManifest =
        VersionManifest(
            latest = LatestVersions(release = "1.21.8", snapshot = "25w31a"),
            versions =
                listOf(
                    VersionManifestEntry("25w31a", "snapshot", "https://example.invalid/25w31a.json"),
                    VersionManifestEntry("1.21.8", "release", "https://example.invalid/1.21.8.json"),
                    VersionManifestEntry("1.21.7", "release", "https://example.invalid/1.21.7.json"),
                    VersionManifestEntry("1.20.1", "release", "https://example.invalid/1.20.1.json"),
                ),
        )

    private fun pages(themeController: ThemeController): List<UiPage> =
        listOf(
            InstancesPage(manifestStub, installerStub),
            DownloadsPage(),
            PluginsPage(plugins),
            SettingsPage(launcher, themeController, safeMode = false, manifests = manifestStub),
        )

    private fun render(
        name: String,
        themeController: ThemeController,
        screen: List<UiPage> = pages(themeController),
    ) {
        Files.createDirectories(screenshots)
        val scene =
            ImageComposeScene(width = 1100, height = 720, density = Density(1f)) {
                AppRoot(themeController) {
                    AppShell(
                        pages = screen,
                        launcher = launcher,
                        safeMode = false,
                        pluginCount = plugins.size,
                    )
                }
            }
        try {
            // 首帧可能只画了一部分，渲染两帧再截图
            scene.render()
            val data = scene.render().encodeToData() ?: error("PNG 编码失败")
            assertTrue(data.bytes.isNotEmpty())
            Files.write(screenshots.resolve("$name.png"), data.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `渲染默认深色外壳`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        render("shell-dark", controller)
    }

    @Test
    fun `渲染插件主题外壳`() {
        val settings = InMemorySettingsService()
        val sakura =
            SimpleLauncherTheme(
                id = "dev.mmcl.theme.sakura",
                displayName = "樱花",
                dark = false,
                colorScheme = MmclPalettes.Light.copy(
                    primary = androidx.compose.ui.graphics.Color(0xFFD81B60),
                    background = androidx.compose.ui.graphics.Color(0xFFFFF7F8),
                    surface = androidx.compose.ui.graphics.Color(0xFFFFFBFF),
                ),
            )
        settings.putString(ThemeController.THEME_KEY, sakura.id)

        val controller = ThemeController(settings, listOf<LauncherTheme>(sakura))

        render("shell-sakura", controller)
        assertTrue(controller.current.id == sakura.id)
    }

    @Test
    fun `渲染设置页（主题列表）`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        val settingsPage = SettingsPage(launcher, controller, safeMode = false, manifests = manifestStub)

        render("shell-settings", controller, listOf(settingsPage, InstancesPage()))
    }

    @Test
    fun `渲染插件页`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())

        render("shell-plugins", controller, listOf(PluginsPage(plugins), InstancesPage()))
    }

    @Test
    fun `渲染版本清单卡片`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        render("manifest-card", controller, listOf(ManifestPage(sampleManifest), InstancesPage(manifestStub)))
    }

    @Test
    fun `渲染未安装版本源的状态`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())

        render("no-source", controller, listOf(InstancesPage(noSourceService), DownloadsPage()))
    }

    @Test
    fun `渲染安装卡片`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())

        render(
            "install-ready",
            controller,
            listOf(InstallDemoPage(samplePlan, progress = null, busy = false), InstancesPage(manifestStub, installerStub)),
        )
        render(
            "install-progress",
            controller,
            listOf(
                InstallDemoPage(
                    samplePlan,
                    progress = DownloadProgress(completed = 1, total = 2, current = "libraries/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar"),
                    busy = true,
                ),
                InstancesPage(manifestStub, installerStub),
            ),
        )
    }

    @Test
    fun `内置主题 id 固定不变`() {
        assertTrue(BuiltinThemes.DARK_ID == "builtin.dark")
    }

    /** 渲染测试专用的"安装卡片"页面。 */
    private class InstallDemoPage(
        private val plan: InstallPlan,
        private val progress: DownloadProgress?,
        private val busy: Boolean,
    ) : UiPage {
        override val id: String = "dev.mmcl.page.render-install"
        override val title: String = "安装"
        override val icon: UiIcon = UiIcon.DOWNLOAD
        override val group: UiGroup = UiGroup.MAIN
        override val order: Int = 2

        @Composable
        override fun Content(navigator: AppNavigator) {
            PageScaffold(title = "准备版本", subtitle = "下载器接线演示") {
                GamePrepCard(
                    versionId = plan.versionId,
                    plan = plan,
                    progress = progress,
                    report = null,
                    failure = null,
                    javaStatus = "Java 21（系统 JDK）",
                    onLaunch = if (busy) null else ({ }),
                    launchStatus = if (busy) null else "已启动（pid 4242）",
                    running = busy,
                    onStop = {},
                    busy = busy,
                    onCheck = {},
                    onDownload = {},
                )
            }
        }
    }

    /** 渲染测试专用的"版本清单"页面，用来给摘要卡片出图。 */
    private class ManifestPage(private val manifest: VersionManifest) : UiPage {
        override val id: String = "dev.mmcl.page.render-manifest"
        override val title: String = "版本"
        override val icon: UiIcon = UiIcon.DOWNLOAD
        override val group: UiGroup = UiGroup.MAIN
        override val order: Int = 1

        @Composable
        override fun Content(navigator: AppNavigator) {
            PageScaffold(title = "版本清单", subtitle = "来自 Mojang piston-meta，缓存 30 分钟") {
                ManifestSummary(manifest)
            }
        }
    }
}
