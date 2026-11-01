package dev.mmcl.core.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class RateLimiterTest {
    @Test
    fun `按速率排队阻塞`() = runTest {
        var now = 0L
        val waits = mutableListOf<Long>()
        val limiter =
            RateLimiter(
                bytesPerSecond = 1000,
                clock = { now },
                sleeper = { millis ->
                    waits += millis
                    now += millis * 1_000_000
                },
            )

        limiter.acquire(500)
        assertEquals(emptyList(), waits)

        limiter.acquire(500)
        assertEquals(listOf(500L), waits)

        limiter.acquire(500)
        assertEquals(listOf(500L, 500L), waits)
    }

    @Test
    fun `不限速时不等待`() = runTest {
        var waited = false
        val limiter = RateLimiter(bytesPerSecond = 0, sleeper = { waited = true })

        limiter.acquire(10_000_000)

        assertEquals(false, waited)
    }
}
