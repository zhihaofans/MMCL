package dev.mmcl.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** 当前是否铺了用户背景图；面板需要据此改成半透明，否则会把壁纸整块盖住。 */
val LocalWallpaperActive = compositionLocalOf { false }

/** 壁纸模式下导航栏 / 状态栏的透明度：太低看不清字，太高又挡图。 */
const val WALLPAPER_PANEL_ALPHA: Float = 0.62f

/**
 * 面板底色：没有壁纸时保持原色；有壁纸时按 [alpha] 半透明，
 * 让底下的图透出来（`UI_DESIGN.md` P5 背景图）。
 */
@Composable
fun Color.onWallpaper(alpha: Float = WALLPAPER_PANEL_ALPHA): Color =
    if (LocalWallpaperActive.current) copy(alpha = alpha) else this
