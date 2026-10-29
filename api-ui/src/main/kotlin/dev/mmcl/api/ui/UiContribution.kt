package dev.mmcl.api.ui

import androidx.compose.runtime.Composable

/**
 * UI 扩展点：插件注册一个标签页/面板，由 `app` 统一渲染。
 *
 * 只有 UI 插件可以实现它（需要依赖 `api-ui`），非 UI 插件不得依赖本模块。
 */
interface UiContribution {
    /** 全局唯一标识，例如 `dev.mmcl.ui.logviewer`。 */
    val id: String

    /** 标签页标题。 */
    val title: String

    /** 排序权重，越小越靠前。 */
    val order: Int
        get() = 100

    /** 面板内容；实现里禁止阻塞操作。 */
    @Composable
    fun Content()
}

/** UI 面板注册表：`app` 在启动时读取全部贡献并渲染。 */
interface UiPanelRegistry {
    /** 注册一个 UI 贡献。 */
    fun register(contribution: UiContribution)

    /** 按 [UiContribution.order] 升序返回全部贡献。 */
    fun all(): List<UiContribution>
}
