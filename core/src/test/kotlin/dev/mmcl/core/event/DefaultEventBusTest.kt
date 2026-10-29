package dev.mmcl.core.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class DefaultEventBusTest {
    private val bus = DefaultEventBus(LoggerFactory.getLogger("test"))

    private data class Ping(val value: Int)

    @Test
    fun `订阅者能收到事件`() {
        val received = mutableListOf<Int>()
        bus.subscribe(Ping::class) { received += it.value }

        bus.publish(Ping(1))
        bus.publish(Ping(2))

        assertEquals(listOf(1, 2), received)
    }

    @Test
    fun `单个处理器异常不影响其它处理器`() {
        val received = mutableListOf<Int>()
        bus.subscribe(Ping::class) { error("handler boom") }
        bus.subscribe(Ping::class) { received += it.value }

        bus.publish(Ping(7))

        assertEquals(listOf(7), received)
    }

    @Test
    fun `取消订阅后不再收到事件`() {
        var count = 0
        val subscription = bus.subscribe(Ping::class) { count++ }

        bus.publish(Ping(1))
        subscription.cancel()
        subscription.cancel()
        bus.publish(Ping(2))

        assertEquals(1, count)
    }

    @Test
    fun `没有订阅者时发布不报错`() {
        bus.publish(Ping(42))
        assertTrue(true)
    }
}
