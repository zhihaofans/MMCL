package dev.mmcl.app.theme

import androidx.compose.runtime.mutableStateOf
import dev.mmcl.api.service.SettingsService
import dev.mmcl.api.ui.LauncherTheme
import org.slf4j.LoggerFactory

/**
 * 当前主题的唯一状态源：负责"从设置读取 → 回退 → 切换 → 写回"。
 *
 * `current` 是 Compose `MutableState`，切换后界面立即重组，无需重启。
 *
 * @param settings 持久化设置（app 启动时传入 `FileSettingsService`）
 * @param pluginThemes 插件注册的主题
 * @param builtinThemes 内置主题；同一 id 时内置优先
 */
class ThemeController(
    private val settings: SettingsService,
    pluginThemes: List<LauncherTheme>,
    private val builtinThemes: List<LauncherTheme> = BuiltinThemes.all,
) {
    private val logger = LoggerFactory.getLogger("dev.mmcl.app.theme")

    private val available: List<LauncherTheme> =
        buildList {
            addAll(builtinThemes)
            addAll(pluginThemes)
        }.distinctBy { it.id }

    private val state = mutableStateOf(resolve(settings.getString(THEME_KEY)))

    /** 当前生效的主题。 */
    val current: LauncherTheme
        get() = state.value

    /** 全部可选主题（内置在前）。 */
    fun all(): List<LauncherTheme> = available

    /**
     * 切换到指定主题；id 不存在时回退到内置深色并记录告警。
     */
    fun apply(themeId: String) {
        val theme =
            available.firstOrNull { it.id == themeId }
                ?: run {
                    logger.warn("主题 {} 不存在，回退到 {}", themeId, BuiltinThemes.DARK_ID)
                    fallback()
                }
        state.value = theme
        settings.putString(THEME_KEY, theme.id)
    }

    private fun resolve(themeId: String?): LauncherTheme =
        available.firstOrNull { it.id == themeId } ?: fallback()

    private fun fallback(): LauncherTheme =
        available.firstOrNull { it.id == BuiltinThemes.DARK_ID } ?: available.first()

    companion object {
        /** 设置键：当前主题 id。 */
        const val THEME_KEY: String = "ui.theme"
    }
}
