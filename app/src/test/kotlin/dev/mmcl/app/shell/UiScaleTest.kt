package dev.mmcl.app.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiScaleTest {
    @Test
    fun `解析合法倍数`() {
        assertEquals(1.25f, parseUiScaleOverride("1.25"))
        assertEquals(1.5f, parseUiScaleOverride(" 1.5 "))
        assertEquals(2f, parseUiScaleOverride("2"))
    }

    @Test
    fun `逗号小数也认`() {
        assertEquals(1.25f, parseUiScaleOverride("1,25"))
    }

    @Test
    fun `非法值返回 null`() {
        assertNull(parseUiScaleOverride(null))
        assertNull(parseUiScaleOverride(""))
        assertNull(parseUiScaleOverride("abc"))
        assertNull(parseUiScaleOverride("0.3"))
        assertNull(parseUiScaleOverride("5"))
    }

    @Test
    fun `生效时写入 sun java2d uiScale`() {
        val previous = System.clearProperty("sun.java2d.uiScale")
        try {
            assertEquals(1.25f, applyUiScaleOverride("1.25"))
            assertEquals("1.25", System.getProperty("sun.java2d.uiScale"))
        } finally {
            if (previous == null) {
                System.clearProperty("sun.java2d.uiScale")
            } else {
                System.setProperty("sun.java2d.uiScale", previous)
            }
        }
    }

    @Test
    fun `没有覆盖时不写系统属性`() {
        val previous = System.clearProperty("sun.java2d.uiScale")
        try {
            assertNull(applyUiScaleOverride(null))
            assertNull(System.getProperty("sun.java2d.uiScale"))
        } finally {
            if (previous != null) System.setProperty("sun.java2d.uiScale", previous)
        }
    }
}
