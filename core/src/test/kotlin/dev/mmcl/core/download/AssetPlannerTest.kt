package dev.mmcl.core.download

import dev.mmcl.core.version.AssetIndex
import dev.mmcl.core.version.AssetObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetPlannerTest {
    private val hash = "6a84d4afab797aaf9f6ad283ebcd5899493c5c64"

    @Test
    fun `对象按 SHA-1 分段且地址由来源提供`() {
        val index =
            AssetIndex(
                objects =
                    mapOf(
                        "minecraft/textures/x.png" to AssetObject(hash = hash, size = 123),
                    ),
            )

        val plan = AssetPlanner.plan(index) { h -> "https://cdn.example/$h" }

        assertEquals(1, plan.files.size)
        assertEquals(0, plan.missingUrls)
        val file = plan.files.single()
        assertEquals("assets/objects/6a/$hash", file.relativePath)
        assertEquals("https://cdn.example/$hash", file.url)
        assertEquals(hash, file.sha1)
        assertEquals(123, file.size)
    }

    @Test
    fun `来源不提供地址时只计数不下载`() {
        val index =
            AssetIndex(
                objects =
                    mapOf(
                        "a" to AssetObject(hash = hash, size = 1),
                        "b" to AssetObject(hash = "", size = 1),
                    ),
            )

        val plan = AssetPlanner.plan(index) { null }

        assertTrue(plan.files.isEmpty())
        assertEquals(2, plan.missingUrls)
    }
}
