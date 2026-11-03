package dev.mmcl.app.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import dev.mmcl.app.appearance.withFontFamily
import dev.mmcl.app.theme.ThemeController

/** 壁纸模式下根 Surface 的透明度：比面板更实一点，保证正文可读。 */
private const val WALLPAPER_ROOT_ALPHA = 0.70f

/**
 * 主题包装层：app 负责套 `MaterialTheme`，主题插件只提供数据与可选装饰。
 *
 * 这样主题插件改不坏布局骨架（`UI_DESIGN.md` §3 D3）。
 *
 * @param fontFamily 用户自选字体；为空时用主题自带的排版
 * @param background 用户自选背景图；非空时整窗铺图，面板改半透明（P5 壁纸）
 */
@Composable
fun AppRoot(
    themeController: ThemeController,
    fontFamily: FontFamily? = null,
    background: ImageBitmap? = null,
    content: @Composable () -> Unit,
) {
    val theme = themeController.current
    MaterialTheme(
        colorScheme = theme.colorScheme,
        typography = if (fontFamily != null) theme.typography.withFontFamily(fontFamily) else theme.typography,
        shapes = theme.shapes,
    ) {
        val wallpaper = background
        if (wallpaper == null) {
            ShellSurface { theme.Decorate { content() } }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = wallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                CompositionLocalProvider(LocalWallpaperActive provides true) {
                    ShellSurface { theme.Decorate { content() } }
                }
            }
        }
    }
}

/** 根 Surface：有壁纸时半透明，让底图透出来。 */
@Composable
private fun ShellSurface(content: @Composable () -> Unit) {
    val translucent = LocalWallpaperActive.current
    val color =
        if (translucent) {
            MaterialTheme.colorScheme.background.copy(alpha = WALLPAPER_ROOT_ALPHA)
        } else {
            MaterialTheme.colorScheme.background
        }
    Surface(modifier = Modifier.fillMaxSize(), color = color) { content() }
}
