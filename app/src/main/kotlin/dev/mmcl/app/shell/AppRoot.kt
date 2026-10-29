package dev.mmcl.app.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mmcl.app.theme.ThemeController

/**
 * 主题包装层：app 负责套 `MaterialTheme`，主题插件只提供数据与可选装饰。
 *
 * 这样主题插件改不坏布局骨架（`UI_DESIGN.md` §3 D3）。
 */
@Composable
fun AppRoot(themeController: ThemeController, content: @Composable () -> Unit) {
    val theme = themeController.current
    MaterialTheme(
        colorScheme = theme.colorScheme,
        typography = theme.typography,
        shapes = theme.shapes,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            theme.Decorate { content() }
        }
    }
}
