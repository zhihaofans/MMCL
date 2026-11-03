package dev.mmcl.app.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * 外壳宽度档位。
 *
 * 断点按"导航栏还能不能带文字"来定：宽屏给完整导航，窄窗只留图标。
 * 内容区再靠各页面自己的 `weight` / 列数适配。
 */
enum class ShellWidthClass {
    /** < 900dp：导航栏只留图标，卡片单列。 */
    COMPACT,

    /** 900–1280dp：完整导航，卡片两列。 */
    MEDIUM,

    /** ≥ 1280dp：完整导航，卡片三列。 */
    EXPANDED,
}

/** 外壳的尺寸常量与断点计算。 */
object ShellMetrics {
    /** 窗口允许的最小宽度；小屏 / 高缩放时会被 [minimumWindowSize] 再压低。 */
    val MIN_WINDOW_WIDTH: Dp = 820.dp

    /** 窗口允许的最小高度。 */
    val MIN_WINDOW_HEIGHT: Dp = 560.dp

    /** 展开时的导航栏宽度（图标 + 文字）。 */
    val RAIL_WIDTH_FULL: Dp = 76.dp

    /** 折叠时的导航栏宽度（只有图标）。 */
    val RAIL_WIDTH_COMPACT: Dp = 56.dp

    /** 窄窗断点。 */
    val COMPACT_BREAKPOINT: Dp = 900.dp

    /** 宽屏断点。 */
    val EXPANDED_BREAKPOINT: Dp = 1280.dp
}

/** 按可用宽度给出档位。 */
fun shellWidthClass(width: Dp): ShellWidthClass =
    when {
        width < ShellMetrics.COMPACT_BREAKPOINT -> ShellWidthClass.COMPACT
        width < ShellMetrics.EXPANDED_BREAKPOINT -> ShellWidthClass.MEDIUM
        else -> ShellWidthClass.EXPANDED
    }

/**
 * 主屏可用工作区（已扣掉菜单栏 / 任务栏），单位 dp。
 *
 * 高缩小小屏上，960dp 这类"看起来合理"的最小尺寸可能已经超过屏幕，
 * 所以窗口尺寸一律以这个值为上限。无图形环境（测试、CI）返回 `null`。
 */
fun screenWorkArea(): DpSize? =
    runCatching {
        val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val config = device.defaultConfiguration
        val bounds = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        val scale = config.defaultTransform.scaleX.coerceAtLeast(MIN_USABLE_SCALE)
        val width = (bounds.width - insets.left - insets.right) / scale
        val height = (bounds.height - insets.top - insets.bottom) / scale
        if (width <= 0 || height <= 0) null else DpSize(width.dp, height.dp)
    }.getOrNull()

/**
 * 窗口最小尺寸：不超过工作区的 90%。
 *
 * 例：1366×768 屏在 150% 缩放下可用约 910×512 dp，820×560 会被压到 819×460，
 * 否则窗口最小尺寸比屏幕还大，用户根本没法把它缩回去。
 */
fun minimumWindowSize(workArea: DpSize? = screenWorkArea()): DpSize {
    val base = DpSize(ShellMetrics.MIN_WINDOW_WIDTH, ShellMetrics.MIN_WINDOW_HEIGHT)
    if (workArea == null) return base
    return DpSize(
        minOf(base.width, workArea.width * SAFE_AREA_RATIO),
        minOf(base.height, workArea.height * SAFE_AREA_RATIO),
    )
}

private const val SAFE_AREA_RATIO = 0.9f

private const val MIN_USABLE_SCALE = 0.5
