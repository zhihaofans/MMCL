package dev.mmcl.app.shell

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.mmcl.core.settings.InMemorySettingsService
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowStateStoreTest {
    private val settings = InMemorySettingsService()
    private val screen = Rectangle(0, 0, 1920, 1080)

    private fun store(vararg screens: Rectangle) =
        WindowStateStore(settings, screenBounds = { screens.toList() })

    @Test
    fun `没有保存过时用默认尺寸并居中`() {
        val state = store(screen).load()

        assertEquals(1280, state.width)
        assertEquals(800, state.height)
        assertNull(state.x)
        assertNull(state.y)
        assertEquals(false, state.maximized)
    }

    @Test
    fun `保存后能原样读回`() {
        store(screen).save(SavedWindowState(width = 1400, height = 900, x = 100, y = 80, maximized = true))

        val state = store(screen).load()

        assertEquals(1400, state.width)
        assertEquals(900, state.height)
        assertEquals(100, state.x)
        assertEquals(80, state.y)
        assertEquals(true, state.maximized)
    }

    @Test
    fun `坐标落在屏幕外时退回居中`() {
        // 外接屏拔掉后，原来的 (3000, 200) 已经不在主屏上
        store(screen).save(SavedWindowState(width = 1280, height = 800, x = 3000, y = 200))

        val state = store(screen).load()

        assertNull(state.x)
        assertNull(state.y)
        assertEquals(1280, state.width)
    }

    @Test
    fun `多屏时副屏坐标保留`() {
        val second = Rectangle(1920, 0, 1920, 1080)
        store(screen, second).save(SavedWindowState(width = 1280, height = 800, x = 2000, y = 100))

        val state = store(screen, second).load()

        assertEquals(2000, state.x)
        assertEquals(100, state.y)
    }

    @Test
    fun `取不到屏幕信息时不做越界判断`() {
        store().save(SavedWindowState(width = 1280, height = 800, x = 5000, y = 5000))

        val state = store().load()

        assertEquals(5000, state.x)
        assertEquals(5000, state.y)
    }

    @Test
    fun `尺寸过小或过大都会被夹回合理范围`() {
        store(screen).save(SavedWindowState(width = 320, height = 200))
        val small = store(screen).load()
        assertEquals(WindowStateStore.MIN_WIDTH, small.width)
        assertEquals(WindowStateStore.MIN_HEIGHT, small.height)

        store(screen).save(SavedWindowState(width = 99999, height = 99999))
        val large = store(screen).load()
        assertEquals(WindowStateStore.MAX_WIDTH, large.width)
        assertEquals(WindowStateStore.MAX_HEIGHT, large.height)
    }

    @Test
    fun `可用工作区更小时尺寸被压低`() {
        // 高缩小小屏：工作区只有 900×600 dp，默认 1280×800 放不下
        val store =
            WindowStateStore(
                settings,
                screenBounds = { listOf(screen) },
                availableSize = { DpSize(900.dp, 600.dp) },
            )

        val state = store.load()

        assertTrue(state.width <= 900, "宽度应被压低：${state.width}")
        assertTrue(state.height <= 600, "高度应被压低：${state.height}")
    }

    @Test
    fun `没有设置服务时只返回默认值`() {
        val store = WindowStateStore(settings = null)

        val state = store.load()

        assertEquals(WindowStateStore.DEFAULT_WIDTH, state.width)
        assertEquals(WindowStateStore.DEFAULT_HEIGHT, state.height)
        assertNull(state.x)
    }
}
