package dev.mmcl.api.ui

import androidx.compose.runtime.Composable
import dev.mmcl.api.ui.AppNavigator

/**
 * 插件可挂载的固定插槽（`UI_DESIGN.md` §2）。
 *
 * 布局骨架由启动器决定，插件只能往这些位置加小部件，不能改页面结构。
 */
enum class UiSlot {
    /** 顶栏右侧，标题与版本号之间。 */
    TOP_BAR,

    /** 底部状态栏。 */
    STATUS_BAR,

    /** 首页快捷入口下方的卡片区。 */
    HOME,
}

/**
 * 插槽小部件：插件往 [slot] 里加一块 UI。
 *
 * 通过 `context.services.register(UiWidget::class, ...)` 注册；安全模式下全部忽略。
 * 与 `UiPage` 一样，组合期异常会向上抛（当前只能靠安全模式自救）。
 *
 * @property id 全局唯一，重复时先注册的生效
 * @property slot 挂到哪个插槽
 * @property order 同槽内升序排列
 */
interface UiWidget {
    val id: String

    val slot: UiSlot

    val order: Int get() = 100

    /** 小部件内容；可用 [navigator] 跳转到自己的页面。 */
    @Composable
    fun Content(navigator: AppNavigator)
}
