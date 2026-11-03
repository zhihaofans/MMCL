package dev.mmcl.plugin.theme.sakura

import androidx.compose.ui.graphics.Color
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.ui.LauncherTheme
import dev.mmcl.ui.kit.MmclPalettes
import dev.mmcl.ui.kit.SimpleLauncherTheme

private const val THEME_ID = "dev.mmcl.theme.sakura"

/**
 * 示例主题：只替换几个色槽，其余沿用内置浅色配色，保证对比度可用。
 *
 * 主题插件只能提供配色/排版/可选装饰，改不动布局骨架（`UI_DESIGN.md` §3 D3）。
 */
class SakuraTheme : LauncherTheme by SimpleLauncherTheme(
    id = THEME_ID,
    displayName = "樱花",
    dark = false,
    colorScheme = MmclPalettes.Light.copy(
        primary = Color(0xFFD81B60),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E001D),
        secondary = Color(0xFF8E4A5A),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFFFFF7F8),
        onBackground = Color(0xFF201A1B),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF201A1B),
    ),
)

/** 注册 [SakuraTheme] 的插件入口。 */
class SakuraThemePlugin : Plugin {
    override val id: String = "dev.mmcl.theme.sakura.plugin"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT

    override fun onLoad(context: PluginContext) {
        context.services.register(LauncherTheme::class, SakuraTheme())
        context.logger.info("樱花主题已注册")
    }
}
