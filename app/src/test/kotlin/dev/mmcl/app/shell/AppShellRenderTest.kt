package dev.mmcl.app.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.LauncherTheme
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.ui.UiSlot
import dev.mmcl.api.ui.UiWidget
import dev.mmcl.app.appearance.AppearanceStore
import dev.mmcl.app.i18n.BuiltinStrings
import dev.mmcl.app.i18n.LocaleController
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.app.i18n.MapStrings
import dev.mmcl.app.i18n.Strings
import dev.mmcl.app.pages.DownloadsPage
import dev.mmcl.app.pages.HomePage
import dev.mmcl.app.pages.GameLogPanel
import dev.mmcl.app.pages.InstanceCard
import dev.mmcl.app.pages.InstancesPage
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.core.instance.InstanceStore
import dev.mmcl.app.pages.ManifestSummary
import dev.mmcl.app.pages.PluginsPage
import dev.mmcl.app.pages.SettingsPage
import dev.mmcl.app.theme.BuiltinThemes
import dev.mmcl.app.theme.ThemeController
import dev.mmcl.core.instance.Instance
import dev.mmcl.core.plugin.LoadedPlugin
import dev.mmcl.core.plugin.PluginDescriptor
import dev.mmcl.core.plugin.PluginState
import dev.mmcl.core.settings.InMemorySettingsService
import dev.mmcl.api.capability.JavaRuntimeProvider
import dev.mmcl.api.capability.RuntimeDownload
import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.core.java.JavaRuntimeSources
import dev.mmcl.core.version.LatestVersions
import dev.mmcl.core.version.VersionManifest
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
            version = "0.0.3",
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
                        override val title: String = "示例插件 $id"
                        override val author: String = "MMCL"
                        override val icon: PluginIcon = PluginIcon.UI
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

            override suspend fun fetchManifestJson(): String =
                """
                {
                  "latest": {"release": "26.3", "snapshot": "26.4-snapshot"},
                  "versions": [
                    {"id": "26.3", "type": "release", "url": "https://example.invalid/26.3.json"},
                    {"id": "26.2", "type": "release", "url": "https://example.invalid/26.2.json"},
                    {"id": "26.1", "type": "release", "url": "https://example.invalid/26.1.json"}
                  ]
                }
                """.trimIndent()
        }

    private val manifestStub =
        VersionManifestService(
            cacheFile = Path.of("build", "tmp", "render-manifest.json"),
            logger = org.slf4j.LoggerFactory.getLogger("test.render"),
            sources = { listOf(stubSource) },
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
            HomePage(manifests = manifestStub),
            InstancesPage(manifestStub),
            DownloadsPage(manifestStub),
            PluginsPage(plugins),
            SettingsPage(launcher, themeController, safeMode = false, manifests = manifestStub),
        )

    private val demoWidgets: List<UiWidget> =
        listOf(
            object : UiWidget {
                override val id: String = "demo.top"
                override val slot: UiSlot = UiSlot.TOP_BAR

                @Composable
                override fun Content(navigator: AppNavigator) {
                    Text(text = "DEMO", style = MaterialTheme.typography.labelSmall)
                }
            },
            object : UiWidget {
                override val id: String = "demo.status"
                override val slot: UiSlot = UiSlot.STATUS_BAR

                @Composable
                override fun Content(navigator: AppNavigator) {
                    Text(text = "同步完成", style = MaterialTheme.typography.labelSmall)
                }
            },
            object : UiWidget {
                override val id: String = "demo.home"
                override val slot: UiSlot = UiSlot.HOME

                @Composable
                override fun Content(navigator: AppNavigator) {
                    Text(text = "示例插件卡片：插件可以往首页加内容", style = MaterialTheme.typography.bodySmall)
                }
            },
        )

    private fun chineseStrings(): Strings =
        MapStrings(listOf(BuiltinStrings.simplifiedChinese, BuiltinStrings.english))

    private fun englishStrings(): Strings =
        MapStrings(listOf(BuiltinStrings.english))

    private fun render(
        name: String,
        themeController: ThemeController,
        screen: List<UiPage> = pages(themeController),
        widgets: List<UiWidget> = emptyList(),
        titleBarInset: Dp = 0.dp,
        background: ImageBitmap? = null,
        strings: Strings = chineseStrings(),
        width: Int = 1100,
        height: Int = 720,
        density: Float = 1f,
    ) {
        Files.createDirectories(screenshots)
        val scene =
            ImageComposeScene(width = width, height = height, density = Density(density)) {
                CompositionLocalProvider(LocalStrings provides strings) {
                AppRoot(themeController, background = background) {
                    AppShell(
                        pages = screen,
                        launcher = launcher,
                        safeMode = false,
                        pluginCount = plugins.size,
                        widgets = widgets,
                        titleBarInset = titleBarInset,
                    )
                }
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
        render("shell-dark", controller, widgets = demoWidgets)
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
        val javaSources =
            JavaRuntimeSources(
                providers = {
                    listOf(
                        fakeRuntimeProvider("adoptium", "Adoptium Temurin", 100),
                        fakeRuntimeProvider("foojay:zulu", "Azul Zulu", 110),
                        fakeRuntimeProvider("foojay:microsoft", "Microsoft OpenJDK", 130),
                    )
                },
                settings = InMemorySettingsService(),
            )
        val settingsPage =
            SettingsPage(
                launcher,
                controller,
                safeMode = false,
                manifests = manifestStub,
                javaSources = javaSources,
            )

        render("shell-settings", controller, listOf(settingsPage, InstancesPage()))
    }

    private fun fakeRuntimeProvider(id: String, name: String, priority: Int) =
        object : JavaRuntimeProvider {
            override val id: String = id
            override val displayName: String = name
            override val priority: Int = priority

            override suspend fun resolve(
                majorVersion: Int,
                os: OperatingSystem,
                arch: Architecture,
            ): RuntimeDownload? = null
        }

    @Test
    fun `渲染 macOS 透明标题栏留白`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        // 78dp 是红黄绿按钮占用的宽度，顶栏内容要整体右移
        render("shell-macos-chrome", controller, titleBarInset = 78.dp)
    }

    @Test
    fun `渲染英文界面`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        val pageSettings = InMemorySettingsService()
        val settingsPage =
            SettingsPage(
                launcher,
                controller,
                safeMode = false,
                manifests = manifestStub,
                settings = pageSettings,
                appearance = AppearanceStore(pageSettings),
                localeController = LocaleController(pageSettings),
            )
        render("shell-settings-en", controller, listOf(settingsPage, InstancesPage()), strings = englishStrings())
    }

    @Test
    fun `渲染壁纸背景`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        // 渐变壁纸 + 半透明面板：确认文字仍然清楚、导航栏能透出底色
        render("shell-wallpaper", controller, background = gradientBackground())
    }

    private fun gradientBackground(): ImageBitmap {
        val image = BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.paint = GradientPaint(0f, 0f, java.awt.Color(0x2A4D69), 800f, 500f, java.awt.Color(0xE07A5F))
        graphics.fillRect(0, 0, 800, 500)
        graphics.dispose()
        val bytes = ByteArrayOutputStream()
        ImageIO.write(image, "png", bytes)
        return loadImageBitmap(bytes.toByteArray().inputStream())
    }

    @Test
    fun `渲染窄窗口时导航折叠`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        // 820dp 宽落在 COMPACT 档：导航栏只剩图标
        render("shell-compact", controller, width = 820, height = 620)
    }

    @Test
    fun `渲染窄窗口的实例卡片`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        // 820dp + 4 个操作按钮，检查会不会把标题挤没
        render("instance-card-compact", controller, listOf(InstanceDemoPage(), InstancesPage()), width = 820, height = 620)
    }

    @Test
    fun `渲染 1_5 倍缩放`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        // 1100px / 1.5 = 733dp，同样落进 COMPACT 档，用来抓缩放下的挤压
        render("shell-density-1_5", controller, density = 1.5f)
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

        val instancesWithoutSource =
            InstanceService(
                store = InstanceStore(java.nio.file.Files.createTempDirectory("mmcl-render-instances").resolve("instances.json"), org.slf4j.LoggerFactory.getLogger("test.render")),
                instancesDir = java.nio.file.Files.createTempDirectory("mmcl-render-instance-dirs"),
                logger = org.slf4j.LoggerFactory.getLogger("test.render"),
            ).also { it.create("26.3") }
        render(
            "no-source",
            controller,
            listOf(InstancesPage(noSourceService, instancesWithoutSource), DownloadsPage(noSourceService)),
        )
    }

    @Test
    fun `渲染首页`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        val homeInstances =
            InstanceService(
                store = InstanceStore(java.nio.file.Files.createTempDirectory("mmcl-render-home").resolve("instances.json"), org.slf4j.LoggerFactory.getLogger("test.render")),
                instancesDir = java.nio.file.Files.createTempDirectory("mmcl-render-home-dirs"),
                logger = org.slf4j.LoggerFactory.getLogger("test.render"),
            ).also {
                it.create("26.3")
                it.create("26.2").also { second -> it.markLaunched(second.id) }
            }
        render(
            "home",
            controller,
            listOf(
                HomePage(
                    instances = homeInstances,
                    manifests = manifestStub,
                    playerName = "Steve",
                    widgets = demoWidgets,
                ),
                DownloadsPage(manifestStub),
            ),
        )
        render(
            "home-empty",
            controller,
            listOf(HomePage(manifests = manifestStub), DownloadsPage(manifestStub)),
        )
    }

    @Test
    fun `渲染下载页`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        render("downloads", controller, listOf(DownloadsPage(manifestStub, autoLoad = true), InstancesPage(manifestStub)))
    }

    @Test
    fun `渲染实例卡片`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        render("instance-card", controller, listOf(InstanceDemoPage(), InstancesPage(manifestStub)))
    }

    @Test
    fun `渲染游戏日志面板`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())
        render("game-log", controller, listOf(GameLogDemoPage(), InstancesPage(manifestStub)))
    }

    @Test
    fun `内置主题 id 固定不变`() {
        assertTrue(BuiltinThemes.DARK_ID == "builtin.dark")
    }

    /** 渲染测试专用的"游戏日志"页面。 */
    private class GameLogDemoPage : UiPage {
        override val id: String = "dev.mmcl.page.render-log"
        override val title: String = "日志"
        override val icon: UiIcon = UiIcon.INFO
        override val group: UiGroup = UiGroup.MAIN
        override val order: Int = 2

        @Composable
        override fun Content(navigator: AppNavigator) {
            PageScaffold(title = "游戏", subtitle = "实例、启动与日志") {
                GameLogPanel(
                    title = "游戏日志 · 26.3",
                    lines = listOf(
                        "[22:41:52] [main/INFO] [Minecraft] Setting user: Steve",
                        "[22:41:52] [Render thread/INFO] [Minecraft] Backend library: LWJGL version 3.4.3+4",
                        "[22:41:53] [Render thread/INFO] [SoundEngine] OpenAL initialized on device MacBook Pro Speakers",
                        "[22:41:54] [Render thread/WARN] [TextureManager] Missing asset: minecraft/textures/gui/title/background.png",
                        "[22:41:55] [Render thread/ERROR] [RealmsClient] Couldn't connect to realms",
                        "  com.mojang.realmsclient.exception.RealmsServiceException: Realms authentication error",
                        "[22:41:56] [Render thread/INFO] [Minecraft] Stopping!",
                    ),
                    running = false,
                    exitCode = 0,
                    onClear = {},
                )
            }
        }
    }

    /** 渲染测试专用的"实例列表"页面。 */
    private class InstanceDemoPage : UiPage {
        override val id: String = "dev.mmcl.page.render-instance"
        override val title: String = "实例"
        override val icon: UiIcon = UiIcon.GAME
        override val group: UiGroup = UiGroup.MAIN
        override val order: Int = 1

        @Composable
        override fun Content(navigator: AppNavigator) {
            PageScaffold(title = "游戏", subtitle = "实例、启动与日志") {
                InstanceCard(
                    instance = Instance(id = "26.3", name = "26.3", versionId = "26.3", createdAtEpochMillis = 0),
                    running = false,
                    onLaunch = {},
                    onRepair = {},
                    onEditParams = {},
                    onDelete = {},
                )
                InstanceCard(
                    instance = Instance(id = "26.3-2", name = "26.3 2", versionId = "26.3", createdAtEpochMillis = 1),
                    running = true,
                    onLaunch = {},
                    onDelete = {},
                )
                InstanceCard(
                    instance = Instance(id = "26.2", name = "26.2", versionId = "26.2", createdAtEpochMillis = 2),
                    missingFiles = true,
                    running = false,
                    onLaunch = {},
                    onDelete = {},
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
