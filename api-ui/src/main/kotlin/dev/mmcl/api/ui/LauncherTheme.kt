package dev.mmcl.api.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/**
 * 主题扩展点：通过 `ServiceRegistry.register(LauncherTheme::class, ...)` 注册。
 *
 * 设计为**数据优先**：`app` 负责套用 `MaterialTheme`，插件只提供配色/排版/形状，
 * 再用可选的 [Decorate] 加背景之类的装饰。这样主题插件改不坏布局骨架
 * （导航栏宽度、顶栏、状态栏都由 app 固定）。
 *
 * 注意：`Decorate` 中抛异常会导致组合失败且无法在运行时隔离，
 * 启动器提供 `--safe-mode` 作为自救手段（见 `UI_DESIGN.md` §5）。
 */
interface LauncherTheme {
    /** 全局唯一标识，内置主题用 `builtin.*` 前缀，插件主题用反向域名。 */
    val id: String

    /** 设置页里显示的名字。 */
    val displayName: String

    /** 是否为深色主题，用于系统栏/状态栏与图标取色。 */
    val dark: Boolean

    /** Material3 配色方案，必需。 */
    val colorScheme: ColorScheme

    /** 字体排版，默认使用 Material3 默认值。 */
    val typography: Typography
        get() = Typography()

    /** 圆角等形状，默认使用 Material3 默认值。 */
    val shapes: Shapes
        get() = Shapes()

    /**
     * 可选装饰层：想加背景图、渐变、边框时覆盖，默认原样透传。
     *
     * @param content 启动器外壳，必须被调用一次
     */
    @Composable
    fun Decorate(content: @Composable () -> Unit) {
        content()
    }
}
