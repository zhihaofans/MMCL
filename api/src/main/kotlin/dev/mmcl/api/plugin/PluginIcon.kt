package dev.mmcl.api.plugin

/**
 * 插件图标的语义枚举。
 *
 * 插件不能塞任意图片：`app` 负责把这里的值映射成内置矢量图（和 `UiIcon` 同一套约束，
 * 见 `UI_DESIGN.md`）。新增取值前先确认 `ui-kit` 里有对应图标。
 */
enum class PluginIcon {
    /** 通用插件图标。 */
    DEFAULT,

    /** 账户 / 认证。 */
    AUTH,

    /** 版本清单 / 下载源。 */
    SOURCE,

    /** Java 运行时来源。 */
    RUNTIME,

    /** 主题。 */
    THEME,

    /** 界面页面或部件。 */
    UI,

    /** 工具类。 */
    TOOL,

    /** 语言包。 */
    LOCALE,
}
