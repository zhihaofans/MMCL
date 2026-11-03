package dev.mmcl.app.shell

/** 覆盖界面缩放的环境变量。 */
const val ENV_UI_SCALE: String = "MMCL_UI_SCALE"

/** 覆盖界面缩放的系统属性（环境变量的等价写法）。 */
const val PROPERTY_UI_SCALE: String = "mmcl.uiScale"

/** 允许的缩放范围；超出这个范围基本是填错了。 */
private val UI_SCALE_RANGE = 0.5f..4.0f

/**
 * 解析用户给的缩放覆盖值；非法或超范围返回 `null`。
 *
 * 接受 `1.25`、`1,25` 这种写法（部分地区的小数点是逗号）。
 */
fun parseUiScaleOverride(raw: String?): Float? {
    val text = raw?.trim()?.replace(',', '.') ?: return null
    val value = text.toFloatOrNull() ?: return null
    return value.takeIf { it in UI_SCALE_RANGE }
}

/**
 * 把覆盖值写进 `sun.java2d.uiScale`，必须在任何 AWT / Compose 初始化之前调用。
 *
 * 背景：Linux 上的 GTK 分数缩放（125% / 150%）JDK 经常只报整数倍，
 * 界面会偏小或发糊。JDK 没有可靠的自动修法，只能让用户显式指定一个倍数。
 *
 * @return 实际生效的缩放；没设置或非法时返回 `null`（保持系统默认）
 */
fun applyUiScaleOverride(raw: String? = System.getenv(ENV_UI_SCALE) ?: System.getProperty(PROPERTY_UI_SCALE)): Float? {
    val scale = parseUiScaleOverride(raw) ?: return null
    System.setProperty("sun.java2d.uiScale", scale.toString())
    return scale
}
