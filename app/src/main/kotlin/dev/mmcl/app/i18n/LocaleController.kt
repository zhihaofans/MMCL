package dev.mmcl.app.i18n

import dev.mmcl.api.capability.LocalePack
import dev.mmcl.api.service.SettingsService
import java.util.Locale

/** 设置页里的一个语言选项。 */
data class LocaleOption(
    val locale: String,
    val displayName: String,
    val builtin: Boolean,
)

/**
 * 语言选择与文案查找链。
 *
 * 规则：
 * 1. 设置项 `ui.locale` 为 `auto`（缺省）时跟随系统：`zh*` → 简体中文，其余 → 英文；
 * 2. 选中的语言若是插件语言包，它的 map 排在链首，覆盖内置同名 key；
 * 3. 回退链末尾永远是英文，再缺就返回 key 本身。
 *
 * @param settings 键值设置；为 `null` 时永远跟随系统
 * @param packs 插件注册的语言包；每次求值，插件可能随时注册
 * @param systemLanguageTag 系统语言标签，测试可注入
 */
class LocaleController(
    private val settings: SettingsService?,
    private val packs: () -> List<LocalePack> = { emptyList() },
    private val systemLanguageTag: () -> String = { Locale.getDefault().toLanguageTag() },
) {
    /** 可选语言：内置两种 + 插件注册的（同 locale 的插件不重复列出）。 */
    fun options(): List<LocaleOption> {
        val builtin = setOf(ENGLISH, CHINESE)
        val plugin =
            packs()
                .filter { it.locale !in builtin }
                .distinctBy { it.locale }
                .sortedBy { it.locale }
                .map { LocaleOption(it.locale, it.displayName, builtin = false) }
        return listOf(
            LocaleOption(ENGLISH, "English", builtin = true),
            LocaleOption(CHINESE, "简体中文", builtin = true),
        ) + plugin
    }

    /** 原始设置值：`auto` 或某个 locale。 */
    fun selected(): String =
        settings?.getString(SETTING_KEY)?.takeIf { it.isNotBlank() } ?: AUTO

    /** 真正生效的语言标签。 */
    fun currentLocale(): String {
        val chosen = selected()
        if (chosen != AUTO) return chosen
        return if (systemLanguageTag().lowercase().startsWith("zh")) CHINESE else ENGLISH
    }

    /** 记住选择；传 [AUTO] 恢复跟随系统。 */
    fun select(locale: String) {
        settings?.putString(SETTING_KEY, locale)
    }

    /** 当前语言的查找链。 */
    fun strings(onMissing: (String) -> Unit = {}): Strings {
        val locale = currentLocale()
        val plugin = packs()
            .firstOrNull { it.locale.equals(locale, ignoreCase = true) }
            ?.let { pack -> runCatching { pack.strings() }.getOrNull() }
        val chain =
            buildList {
                plugin?.let(::add)
                if (locale.lowercase().startsWith("zh")) add(BuiltinStrings.simplifiedChinese)
                add(BuiltinStrings.english)
            }
        return MapStrings(chain, onMissing)
    }

    companion object {
        /** 设置键：界面语言；`auto` 表示跟随系统。 */
        const val SETTING_KEY: String = "ui.locale"

        /** 跟随系统。 */
        const val AUTO: String = "auto"

        /** 英文。 */
        const val ENGLISH: String = "en"

        /** 简体中文。 */
        const val CHINESE: String = "zh-CN"
    }
}
