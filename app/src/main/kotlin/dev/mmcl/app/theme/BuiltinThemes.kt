package dev.mmcl.app.theme

import dev.mmcl.ui.kit.MmclPalettes
import dev.mmcl.ui.kit.SimpleLauncherTheme
import dev.mmcl.api.ui.LauncherTheme

/**
 * 内置主题：**必须存在**，插件主题全挂或开启安全模式时用它兜底。
 */
object BuiltinThemes {
    const val DARK_ID: String = "builtin.dark"
    const val LIGHT_ID: String = "builtin.light"

    val dark: LauncherTheme =
        SimpleLauncherTheme(
            id = DARK_ID,
            displayName = "深色（内置）",
            dark = true,
            colorScheme = MmclPalettes.Dark,
        )

    val light: LauncherTheme =
        SimpleLauncherTheme(
            id = LIGHT_ID,
            displayName = "浅色（内置）",
            dark = false,
            colorScheme = MmclPalettes.Light,
        )

    /** 内置主题列表，顺序即设置页展示顺序。 */
    val all: List<LauncherTheme> = listOf(dark, light)
}
