package dev.mmcl.ui.kit

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import dev.mmcl.api.ui.LauncherTheme

/**
 * 主题插件的推荐基类：只给配色（通常基于 [MmclPalettes] 改几个色槽）就能做出一个主题。
 *
 * 需要加背景图等装饰时，用 [decorate] 传入一个包装 Composable。
 *
 * @property decorate 可选装饰层，必须调用一次传入的 content
 */
class SimpleLauncherTheme(
    override val id: String,
    override val displayName: String,
    override val dark: Boolean,
    override val colorScheme: ColorScheme,
    override val typography: Typography = Typography(),
    override val shapes: Shapes = Shapes(),
    private val decorate: (@Composable (@Composable () -> Unit) -> Unit)? = null,
) : LauncherTheme {
    @Composable
    override fun Decorate(content: @Composable () -> Unit) {
        val decoration = decorate
        if (decoration == null) content() else decoration(content)
    }
}
