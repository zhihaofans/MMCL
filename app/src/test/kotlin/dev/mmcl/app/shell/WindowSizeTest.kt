package dev.mmcl.app.shell

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowSizeTest {
    @Test
    fun `宽度断点`() {
        assertEquals(ShellWidthClass.COMPACT, shellWidthClass(820.dp))
        assertEquals(ShellWidthClass.COMPACT, shellWidthClass(899.dp))
        assertEquals(ShellWidthClass.MEDIUM, shellWidthClass(900.dp))
        assertEquals(ShellWidthClass.MEDIUM, shellWidthClass(1279.dp))
        assertEquals(ShellWidthClass.EXPANDED, shellWidthClass(1280.dp))
        assertEquals(ShellWidthClass.EXPANDED, shellWidthClass(2560.dp))
    }

    @Test
    fun `取不到屏幕信息时用基准最小尺寸`() {
        assertEquals(DpSize(820.dp, 560.dp), minimumWindowSize(workArea = null))
    }

    @Test
    fun `大屏上最小尺寸保持基准值`() {
        val min = minimumWindowSize(DpSize(1920.dp, 1080.dp))

        assertEquals(820.dp, min.width)
        assertEquals(560.dp, min.height)
    }

    @Test
    fun `高缩小小屏上最小尺寸被工作区压低`() {
        // 1366×768 屏在 150% 缩放下可用约 910×512 dp
        val min = minimumWindowSize(DpSize(910.dp, 512.dp))

        assertTrue(min.width < 820.dp, "宽度应被压低：${min.width}")
        assertTrue(min.height < 560.dp, "高度应被压低：${min.height}")
        assertEquals(910.dp * 0.9f, min.width)
    }
}
