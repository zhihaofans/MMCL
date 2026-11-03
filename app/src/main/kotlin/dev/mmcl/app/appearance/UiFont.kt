package dev.mmcl.app.appearance

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.nio.file.Path
import java.awt.Font as AwtFont

/**
 * 把用户选的字体文件加载成 [FontFamily]。
 *
 * 加载失败（文件损坏、不是字体、格式不支持）返回 `null`，调用方退回默认字体——
 * 一个坏掉的字体文件不应该让启动器起不来。
 */
fun loadFontFamily(path: Path?): FontFamily? {
    if (path == null) return null
    return runCatching {
        // 组合式 Font(...) 是懒加载：坏文件要到渲染时才炸。先用 AWT 解析一次做校验，
        // 解析失败会抛 FontFormatException / IOException，被 runCatching 收走
        AwtFont.createFont(AwtFont.TRUETYPE_FONT, path.toFile())
        FontFamily(Font(path.toFile(), FontWeight.Normal, FontStyle.Normal))
    }.getOrNull()
}

/**
 * 把排版里所有样式的字体族换成 [family]，保留字号、行高等其余设置。
 *
 * Material3 的 [Typography] 没有"默认字体族"这种属性，只能逐个样式替换。
 */
fun Typography.withFontFamily(family: FontFamily): Typography =
    copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
