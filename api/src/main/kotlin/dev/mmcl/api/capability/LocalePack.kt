package dev.mmcl.api.capability

/**
 * 语言包扩展点：插件通过 `ServiceRegistry.register(LocalePack::class, ...)` 提供额外语言。
 *
 * 应用自带 `en` 与 `zh-CN` 两种内置语言；插件语言包按 `locale` 覆盖同名内置语言，
 * 缺失的 key 依次回退到 `zh-CN`（当选中语言是中文时）和 `en`。
 *
 * key 用层级命名，例如 `page.settings.title`、`settings.section.appearance`。
 */
interface LocalePack {
    /** BCP-47 语言标签，例如 `zh-CN`、`ja-JP`。 */
    val locale: String

    /** 设置页里显示的名字，用该语言自己的写法，例如 `日本語`。 */
    val displayName: String

    /** key → 文案；缺的 key 不要放，交给回退链处理。 */
    fun strings(): Map<String, String>
}
