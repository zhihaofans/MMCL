package dev.mmcl.app

import androidx.compose.runtime.Composable
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiRegistryTest {
    private class FakePage(
        override val id: String,
        override val group: UiGroup,
        override val order: Int,
        override val title: String = id,
    ) : UiPage {
        @Composable
        override fun Content(navigator: AppNavigator) = Unit
    }

    @Test
    fun `同 id 以内置页面为准`() {
        val builtin = FakePage("x", UiGroup.MAIN, 0, "内置")
        val plugin = FakePage("x", UiGroup.TOOLS, 200, "插件")

        val pages = UiRegistry(listOf(builtin), listOf(plugin)).pages

        assertEquals(1, pages.size)
        assertEquals("内置", pages.single().title)
    }

    @Test
    fun `按分组与 order 排序`() {
        val system = FakePage("sys", UiGroup.SYSTEM, 0)
        val main = FakePage("main", UiGroup.MAIN, 50)
        val toolB = FakePage("tool-b", UiGroup.TOOLS, 200)
        val toolA = FakePage("tool-a", UiGroup.TOOLS, 100)

        val pages = UiRegistry(listOf(system, main), listOf(toolB, toolA)).pages

        assertEquals(listOf("main", "tool-a", "tool-b", "sys"), pages.map { it.id })
    }

    @Test
    fun `安全模式过滤插件页面`() {
        val builtin = FakePage("builtin", UiGroup.MAIN, 0)
        val plugin = FakePage("plugin", UiGroup.TOOLS, 100)

        val pages = UiRegistry(listOf(builtin), listOf(plugin), safeMode = true).pages

        assertEquals(listOf("builtin"), pages.map { it.id })
        assertTrue(pages.none { it.id == "plugin" })
    }
}
