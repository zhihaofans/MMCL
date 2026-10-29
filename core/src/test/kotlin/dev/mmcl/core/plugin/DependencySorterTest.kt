package dev.mmcl.core.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencySorterTest {
    private val sorter = DependencySorter()

    private fun plugin(id: String, vararg dependencies: String) =
        PluginDescriptor(
            id = id,
            version = "1.0.0",
            dependencies = dependencies.toList(),
            plugin = FakePlugin(id, dependencies = dependencies.toList()),
        )

    private fun optionalPlugin(id: String, optional: String) =
        PluginDescriptor(
            id = id,
            version = "1.0.0",
            optionalDependencies = listOf(optional),
            plugin = FakePlugin(id, optionalDependencies = listOf(optional)),
        )

    @Test
    fun `依赖一定排在被依赖者之前`() {
        val result = sorter.sort(listOf(plugin("c", "b"), plugin("b", "a"), plugin("a")))

        assertEquals(listOf("a", "b", "c"), result.order)
        assertTrue(result.cyclic.isEmpty())
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `缺失硬依赖的插件被标记为 missing`() {
        val result = sorter.sort(listOf(plugin("a", "ghost")))

        assertTrue(result.order.isEmpty())
        assertEquals(listOf("ghost"), result.missing["a"])
    }

    @Test
    fun `缺失依赖会向依赖者传递`() {
        val result = sorter.sort(listOf(plugin("a", "ghost"), plugin("b", "a")))

        assertTrue(result.order.isEmpty())
        assertTrue(result.missing.keys.containsAll(listOf("a", "b")))
    }

    @Test
    fun `循环依赖全部被标记且不影响无关插件`() {
        val result = sorter.sort(listOf(plugin("a", "b"), plugin("b", "a"), plugin("c")))

        assertEquals(listOf("c"), result.order)
        assertEquals(setOf("a", "b"), result.cyclic)
    }

    @Test
    fun `依赖了循环插件的插件同样不可用`() {
        val result = sorter.sort(listOf(plugin("a", "b"), plugin("b", "a"), plugin("c", "a")))

        assertTrue(result.cyclic.containsAll(setOf("a", "b", "c")))
        assertTrue(result.order.isEmpty())
    }

    @Test
    fun `自依赖按循环处理`() {
        val result = sorter.sort(listOf(plugin("a", "a")))

        assertEquals(setOf("a"), result.cyclic)
        assertTrue(result.order.isEmpty())
    }

    @Test
    fun `软依赖不影响排序与可用性`() {
        val result = sorter.sort(listOf(optionalPlugin("x", "y")))
        val withY = sorter.sort(listOf(optionalPlugin("x", "y"), plugin("y")))

        assertEquals(listOf("x"), result.order)
        assertTrue(result.missing.isEmpty())
        assertEquals(setOf("x", "y"), withY.order.toSet())
        assertTrue(withY.missing.isEmpty())
    }

    @Test
    fun `同层节点顺序稳定`() {
        val result = sorter.sort(listOf(plugin("z"), plugin("a"), plugin("m")))

        assertEquals(listOf("a", "m", "z"), result.order)
    }
}
