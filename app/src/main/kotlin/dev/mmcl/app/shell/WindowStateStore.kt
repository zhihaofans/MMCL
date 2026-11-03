package dev.mmcl.app.shell

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import dev.mmcl.api.service.SettingsService
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlin.math.roundToInt

/** 一次窗口几何快照；`x`/`y` 为空表示"位置未指定"，由窗口居中。 */
data class SavedWindowState(
    val width: Int,
    val height: Int,
    val x: Int? = null,
    val y: Int? = null,
    val maximized: Boolean = false,
)

/**
 * 窗口大小、位置与最大化状态的持久化（`UI_DESIGN.md` P5）。
 *
 * 越界修正：拔掉外接屏、改分辨率以后再启动，保存的坐标可能落在屏幕外。
 * 这里只要左上角在任一屏幕内就保留位置，否则丢弃坐标退回居中，
 * 避免窗口开到看不见的地方。
 *
 * @param settings 键值设置；为 `null` 时只做默认值，不持久化
 * @param screenBounds 屏幕矩形；测试可注入，默认取 AWT 的显示器信息
 */
class WindowStateStore(
    private val settings: SettingsService?,
    private val screenBounds: () -> List<Rectangle> = ::currentScreenBounds,
    private val availableSize: () -> DpSize? = ::screenWorkArea,
) {
    /** 读取并修正保存的窗口状态。 */
    fun load(): SavedWindowState {
        // 上限优先服从当前屏幕的可用工作区：高缩小小屏上 1280dp 可能根本放不下
        val available = availableSize()
        val maxWidth = available?.width?.value?.toInt()?.coerceAtMost(MAX_WIDTH) ?: MAX_WIDTH
        val maxHeight = available?.height?.value?.toInt()?.coerceAtMost(MAX_HEIGHT) ?: MAX_HEIGHT
        val width =
            (intOf(KEY_WIDTH) ?: DEFAULT_WIDTH).coerceIn(minOf(MIN_WIDTH, maxWidth), maxWidth)
        val height =
            (intOf(KEY_HEIGHT) ?: DEFAULT_HEIGHT).coerceIn(minOf(MIN_HEIGHT, maxHeight), maxHeight)
        val savedX = intOf(KEY_X)
        val savedY = intOf(KEY_Y)
        val position =
            if (savedX != null && savedY != null && isVisible(savedX, savedY)) savedX to savedY else null
        return SavedWindowState(
            width = width,
            height = height,
            x = position?.first,
            y = position?.second,
            maximized = settings?.getString(KEY_MAXIMIZED)?.toBooleanStrictOrNull() ?: false,
        )
    }

    /** 写入窗口状态；`x`/`y` 为 `null` 时删除对应键。 */
    fun save(state: SavedWindowState) {
        val target = settings ?: return
        target.putString(KEY_WIDTH, state.width.toString())
        target.putString(KEY_HEIGHT, state.height.toString())
        target.putString(KEY_X, state.x?.toString())
        target.putString(KEY_Y, state.y?.toString())
        target.putString(KEY_MAXIMIZED, state.maximized.toString())
    }

    private fun intOf(key: String): Int? = settings?.getString(key)?.trim()?.toIntOrNull()

    /** 左上角是否落在某个屏幕里（留一点余量，避免只剩一条边）。 */
    private fun isVisible(x: Int, y: Int): Boolean {
        val screens = screenBounds()
        if (screens.isEmpty()) return true
        return screens.any { screen -> screen.contains(x + VISIBLE_MARGIN, y + VISIBLE_MARGIN) }
    }

    companion object {
        /** 设置键前缀。 */
        const val KEY_WIDTH: String = "window.width"
        const val KEY_HEIGHT: String = "window.height"
        const val KEY_X: String = "window.x"
        const val KEY_Y: String = "window.y"
        const val KEY_MAXIMIZED: String = "window.maximized"

        /** 默认尺寸：1280×800，够看四列卡片又不至于占满小屏。 */
        const val DEFAULT_WIDTH: Int = 1280
        const val DEFAULT_HEIGHT: Int = 800

        /** 最小尺寸：再小布局会挤成一列。 */
        const val MIN_WIDTH: Int = 960
        const val MIN_HEIGHT: Int = 600

        /** 上限：防止异常值把窗口撑到离谱大小。 */
        const val MAX_WIDTH: Int = 10000
        const val MAX_HEIGHT: Int = 10000

        private const val VISIBLE_MARGIN = 48

        /** 默认屏幕矩形；无图形环境（测试、CI）返回空列表。 */
        private fun currentScreenBounds(): List<Rectangle> =
            runCatching {
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .screenDevices
                    .map { it.defaultConfiguration.bounds }
            }.getOrDefault(emptyList())
    }
}

/** 把当前窗口状态转成可持久化的快照；位置未指定时 `x`/`y` 为 `null`。 */
fun WindowState.toSavedWindowState(): SavedWindowState {
    val current = position
    val specified = current.isSpecified
    return SavedWindowState(
        width = size.width.value.roundToInt(),
        height = size.height.value.roundToInt(),
        x = if (specified) current.x.value.roundToInt() else null,
        y = if (specified) current.y.value.roundToInt() else null,
        maximized = placement == WindowPlacement.Maximized,
    )
}
