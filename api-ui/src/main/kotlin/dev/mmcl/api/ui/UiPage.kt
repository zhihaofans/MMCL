package dev.mmcl.api.ui

import androidx.compose.runtime.Composable

/**
 * 导航栏图标：插件不能塞任意图片，只能从这个枚举里选，由 `ui-kit` 映射成内置矢量图。
 */
enum class UiIcon {
    BACK,
    GAME,
    DOWNLOAD,
    SETTINGS,
    PLUGIN,
    ACCOUNT,
    HOME,
    WRENCH,
    INFO,
    EXTENSION,
}

/**
 * 导航分组：`MAIN` 在上，`TOOLS` 居中（插件页默认落这里），`SYSTEM` 沉底。
 */
enum class UiGroup {
    MAIN,
    TOOLS,
    SYSTEM,
}

/**
 * 一个完整页面，渲染在内容区。
 *
 * 通过 `ServiceRegistry.register(UiPage::class, ...)` 注册；`app` 负责收集、去重与渲染。
 * 内置页面用 0..99 的 [order]，插件页面请用 100 以上。
 */
interface UiPage {
    /** 全局唯一标识，例如 `dev.mmcl.ui.about`。 */
    val id: String

    /** 导航栏与页面标题。 */
    val title: String

    /** 导航图标。 */
    val icon: UiIcon
        get() = UiIcon.EXTENSION

    /** 所属导航分组。 */
    val group: UiGroup
        get() = UiGroup.TOOLS

    /** 同组内排序，越小越靠前。 */
    val order: Int
        get() = 100

    /**
     * 页面内容。
     *
     * 禁止阻塞操作；不要在后台线程直接改 Compose 状态（用 `StateFlow` + `collectAsState`）。
     *
     * @param navigator 页面跳转入口，由 `app` 提供
     */
    @Composable
    fun Content(navigator: AppNavigator)
}

/**
 * 页面导航入口：用于读取当前路由、跳转到别的页面。
 *
 * 实现由 `app` 提供且全局唯一，插件只读不实现。
 */
interface AppNavigator {
    /** 当前页面 id。在 Composable 中读取会随路由变化触发重组。 */
    val currentPageId: String

    /** 跳转到指定页面；目标不存在时忽略并记录日志。 */
    fun navigate(pageId: String)

    /** 返回上一页；没有历史时忽略。 */
    fun back()
}
