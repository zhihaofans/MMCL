package dev.mmcl.app.shell

import androidx.compose.runtime.mutableStateListOf
import dev.mmcl.api.ui.AppNavigator

/**
 * [AppNavigator] 的唯一实现：维护一个简单的页面栈。
 *
 * - 侧栏点击走 [select]（清空历史）；
 * - 页面内跳转走 [navigate]（压栈，可用 [back] 返回）。
 */
class NavigationState(initialPageId: String) : AppNavigator {
    private val history = mutableStateListOf(initialPageId)

    override val currentPageId: String
        get() = history.last()

    override fun navigate(pageId: String) {
        if (pageId == history.last()) return
        history.add(pageId)
    }

    override fun back() {
        if (history.size > 1) history.removeAt(history.lastIndex)
    }

    /** 侧栏直接选中：清空历史，不产生返回栈。 */
    fun select(pageId: String) {
        if (pageId == history.last()) return
        history.clear()
        history.add(pageId)
    }

    /** 是否还有可返回的历史。 */
    fun canGoBack(): Boolean = history.size > 1
}
