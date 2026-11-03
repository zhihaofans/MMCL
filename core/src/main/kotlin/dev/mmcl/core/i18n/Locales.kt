package dev.mmcl.core.i18n

import java.util.Locale

/**
 * 界面语言设置项与解析规则。
 *
 * `ui.locale` 存的是用户选择：`auto`（缺省，跟随系统）或具体语言标签。
 * app 的语言查找和 `PluginContext.locale` 都走这里，避免两处规则走偏。
 */
object Locales {
    /** 设置键。 */
    const val KEY: String = "ui.locale"

    /** 跟随系统。 */
    const val AUTO: String = "auto"

    /** 英文（兜底语言）。 */
    const val ENGLISH: String = "en"

    /** 简体中文。 */
    const val CHINESE: String = "zh-CN"

    /**
     * 把设置值解析成具体语言。
     *
     * @param raw `ui.locale` 的原始值，可能是 `null` / `auto` / `zh-CN` 等
     * @param systemLanguageTag 系统语言标签，测试可注入
     */
    fun resolve(raw: String?, systemLanguageTag: String = Locale.getDefault().toLanguageTag()): String {
        val chosen = raw?.trim()?.takeIf { it.isNotEmpty() } ?: AUTO
        if (chosen != AUTO) return chosen
        return if (systemLanguageTag.lowercase().startsWith("zh")) CHINESE else ENGLISH
    }
}
