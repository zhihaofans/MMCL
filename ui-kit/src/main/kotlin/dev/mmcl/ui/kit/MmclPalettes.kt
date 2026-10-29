package dev.mmcl.ui.kit

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * MMCL 的默认配色。插件做主题时建议以它为基底，只改需要的色槽，
 * 避免自己从零拼一套颜色导致可读性（对比度）出问题。
 */
object MmclPalettes {
    /** 默认深色：蓝灰背景 + 亮蓝主色。 */
    val Dark: ColorScheme =
        darkColorScheme(
            primary = Color(0xFF8AB4F8),
            onPrimary = Color(0xFF00315C),
            primaryContainer = Color(0xFF1B3B60),
            onPrimaryContainer = Color(0xFFD6E4FF),
            secondary = Color(0xFFB9C7DC),
            onSecondary = Color(0xFF233143),
            background = Color(0xFF111418),
            onBackground = Color(0xFFE2E6EA),
            surface = Color(0xFF171B20),
            onSurface = Color(0xFFE2E6EA),
            surfaceVariant = Color(0xFF2A3038),
            onSurfaceVariant = Color(0xFFC2C9D2),
            outline = Color(0xFF4A5460),
        )

    /** 默认浅色。 */
    val Light: ColorScheme =
        lightColorScheme(
            primary = Color(0xFF1C64D9),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD7E3FF),
            onPrimaryContainer = Color(0xFF001B3F),
            secondary = Color(0xFF4A5D78),
            onSecondary = Color(0xFFFFFFFF),
            background = Color(0xFFF7F9FC),
            onBackground = Color(0xFF1A1C1E),
            surface = Color(0xFFFDFDFF),
            onSurface = Color(0xFF1A1C1E),
            surfaceVariant = Color(0xFFE1E6EC),
            onSurfaceVariant = Color(0xFF44474C),
            outline = Color(0xFF74777D),
        )
}
