package dev.mmcl.app.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.ui.LauncherTheme
import dev.mmcl.api.ui.UiPage
import dev.mmcl.app.pages.DownloadsPage
import dev.mmcl.app.pages.InstancesPage
import dev.mmcl.app.pages.PluginsPage
import dev.mmcl.app.pages.SettingsPage
import dev.mmcl.app.theme.BuiltinThemes
import dev.mmcl.app.theme.ThemeController
import dev.mmcl.core.plugin.LoadedPlugin
import dev.mmcl.core.plugin.PluginDescriptor
import dev.mmcl.core.plugin.PluginState
import dev.mmcl.core.settings.InMemorySettingsService
import dev.mmcl.ui.kit.MmclPalettes
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

    private fun pages(themeController: ThemeController): List<UiPage> =
        listOf(
            InstancesPage(),
            DownloadsPage(),
            PluginsPage(plugins),
            SettingsPage(launcher, themeController, safeMode = false),
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
        val settingsPage = SettingsPage(launcher, controller, safeMode = false)

        render("shell-settings", controller, listOf(settingsPage, InstancesPage()))
    }

    @Test
    fun `渲染插件页`() {
        val controller = ThemeController(InMemorySettingsService(), emptyList())

        render("shell-plugins", controller, listOf(PluginsPage(plugins), InstancesPage()))
    }

    @Test
    fun `内置主题 id 固定不变`() {
        assertTrue(BuiltinThemes.DARK_ID == "builtin.dark")
    }
}
