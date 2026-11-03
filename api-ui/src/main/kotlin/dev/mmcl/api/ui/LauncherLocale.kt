package dev.mmcl.api.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * 当前界面语言（BCP-47，例如 `en`、`zh-CN`），由 `app` 注入。
 *
 * UI 插件在 `@Composable` 里读它来选文案；语言在设置里切换后会触发重组。
 * 非 UI 插件请用 `PluginContext.locale`。
 */
val LocalLauncherLocale = compositionLocalOf { "en" }
