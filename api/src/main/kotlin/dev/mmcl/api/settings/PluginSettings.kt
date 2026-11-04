package dev.mmcl.api.settings

/**
 * 插件设置页里的一项。
 *
 * 声明式描述：插件只报"要什么设置"，由 `app` 渲染控件并负责读写 `SettingsService`，
 * 这样非 UI 插件也能有设置页（不需要依赖 Compose）。
 */
sealed interface SettingItem {
    /** `SettingsService` 里的键。 */
    val key: String

    /** 该项在当前语言下的标题。 */
    val title: String

    /** 可选的说明文字。 */
    val description: String?
        get() = null

    /** 单行文本。 */
    data class Text(
        override val key: String,
        override val title: String,
        override val description: String? = null,
        val placeholder: String? = null,
        val defaultValue: String = "",
    ) : SettingItem

    /** 开关，值存 `true` / `false`。 */
    data class Toggle(
        override val key: String,
        override val title: String,
        override val description: String? = null,
        val defaultValue: Boolean = false,
    ) : SettingItem

    /** 单选，值存 [ChoiceOption.value]。 */
    data class Choice(
        override val key: String,
        override val title: String,
        override val description: String? = null,
        val options: List<ChoiceOption>,
        val defaultValue: String? = null,
    ) : SettingItem
}

/** [SettingItem.Choice] 的一个选项。 */
data class ChoiceOption(
    val value: String,
    val label: String,
)

/**
 * 插件设置区扩展点：通过 `ServiceRegistry.register(SettingsSection::class, ...)` 注册。
 *
 * `app` 在设置页按注册顺序渲染；`locale` 是当前界面语言，插件据此返回对应语言的文案。
 */
interface SettingsSection {
    /** 全局唯一标识，通常是插件 id。 */
    val id: String

    /** 分区标题（按当前语言）。 */
    fun title(locale: String): String

    /** 可选的说明（按当前语言）。 */
    fun description(locale: String): String? = null

    /** 分区里的设置项（按当前语言）。 */
    fun items(locale: String): List<SettingItem>
}
