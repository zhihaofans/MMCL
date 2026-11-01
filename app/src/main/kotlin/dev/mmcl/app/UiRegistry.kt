package dev.mmcl.app

import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.ui.UiWidget

/**
 * 页面注册表：合并内置页面与插件页面，负责去重、分组排序与安全模式过滤。
 *
 * 规则（`UI_DESIGN.md` §3 D2/D4）：
 * - 同一 id 以内置页面为准，插件不能顶掉内置页；
 * - 按 `group` → `order` → `id` 排序，保证每次启动顺序一致；
 * - 安全模式下忽略全部插件页面。
 *
 * @param builtinPages 启动器自带页面
 * @param pluginPages 插件通过 `ServiceRegistry` 注册的页面
 * @param pluginWidgets 插件注册的插槽小部件
 * @param safeMode 是否跳过插件页面与插件小部件
 */
class UiRegistry(
    builtinPages: List<UiPage>,
    pluginPages: List<UiPage>,
    pluginWidgets: List<UiWidget> = emptyList(),
    private val safeMode: Boolean = false,
) {
    /** 最终渲染的页面列表，至少包含内置页面。 */
    val pages: List<UiPage> =
        buildList {
            addAll(builtinPages)
            if (!safeMode) addAll(pluginPages)
        }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.group.ordinal }, { it.order }, { it.id }))

    /** 最终渲染的插槽小部件，按插槽 → order → id 排序。 */
    val widgets: List<UiWidget> =
        if (safeMode) {
            emptyList()
        } else {
            pluginWidgets
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.slot.ordinal }, { it.order }, { it.id }))
        }
}
