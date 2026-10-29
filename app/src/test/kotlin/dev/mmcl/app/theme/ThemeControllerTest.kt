package dev.mmcl.app.theme

import dev.mmcl.core.settings.InMemorySettingsService
import dev.mmcl.ui.kit.MmclPalettes
import dev.mmcl.ui.kit.SimpleLauncherTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeControllerTest {
    private val settings = InMemorySettingsService()
    private val pluginTheme =
        SimpleLauncherTheme(
            id = "dev.mmcl.theme.test",
            displayName = "测试主题",
            dark = true,
            colorScheme = MmclPalettes.Dark,
        )

    @Test
    fun `默认使用内置深色`() {
        val controller = ThemeController(settings, emptyList())

        assertEquals(BuiltinThemes.DARK_ID, controller.current.id)
    }

    @Test
    fun `读取已保存的主题`() {
        settings.putString(ThemeController.THEME_KEY, BuiltinThemes.LIGHT_ID)

        val controller = ThemeController(settings, emptyList())

        assertEquals(BuiltinThemes.LIGHT_ID, controller.current.id)
    }

    @Test
    fun `未知主题回退到内置深色`() {
        settings.putString(ThemeController.THEME_KEY, "dev.mmcl.theme.missing")

        val controller = ThemeController(settings, listOf(pluginTheme))

        assertEquals(BuiltinThemes.DARK_ID, controller.current.id)
    }

    @Test
    fun `切换插件主题并持久化`() {
        val controller = ThemeController(settings, listOf(pluginTheme))

        controller.apply(pluginTheme.id)

        assertEquals(pluginTheme.id, controller.current.id)
        assertEquals(pluginTheme.id, settings.getString(ThemeController.THEME_KEY))
        assertEquals(pluginTheme.id, ThemeController(settings, listOf(pluginTheme)).current.id)
    }

    @Test
    fun `内置主题 id 不允许被插件覆盖`() {
        val impostor =
            SimpleLauncherTheme(
                id = BuiltinThemes.DARK_ID,
                displayName = "假的深色",
                dark = true,
                colorScheme = MmclPalettes.Dark,
            )
        val controller = ThemeController(settings, listOf(impostor))

        assertEquals("深色（内置）", controller.current.displayName)
        assertEquals(1, controller.all().count { it.id == BuiltinThemes.DARK_ID })
    }
}
