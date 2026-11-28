package dev.mmcl.ui.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemInfoTest {
    private fun profile(cores: Int, memoryMb: Long) =
        SystemProfile(osName = "Test", osVersion = "1", arch = "x86_64", cpuCores = cores, memoryMb = memoryMb)

    @Test
    fun `满足最低配置时不提示低配`() {
        val check = SystemInfo.evaluate(profile(8, 16 * 1024))

        assertFalse(check.belowMinimum)
        assertTrue(check.notes.isEmpty())
    }

    @Test
    fun `少于 4 核被标记`() {
        val check = SystemInfo.evaluate(profile(2, 16 * 1024))

        assertTrue(check.belowMinimum)
        assertTrue(SystemNote.LOW_CORES in check.notes)
    }

    @Test
    fun `内存低于 8G 被标记`() {
        val check = SystemInfo.evaluate(profile(8, 6 * 1024))

        assertTrue(check.belowMinimum)
        assertEquals(listOf(SystemNote.LOW_MEMORY), check.notes)
    }

    @Test
    fun `8 到 12G 提示核显可能不够`() {
        val check = SystemInfo.evaluate(profile(8, 10 * 1024))

        assertFalse(check.belowMinimum)
        assertEquals(listOf(SystemNote.INTEGRATED_MAY_NEED_MORE), check.notes)
    }

    @Test
    fun `读不到内存时只提示未知`() {
        val check = SystemInfo.evaluate(profile(8, 0))

        assertEquals(listOf(SystemNote.UNKNOWN_MEMORY), check.notes)
    }
}
