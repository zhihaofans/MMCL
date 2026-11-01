package dev.mmcl.core.download

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 令牌桶限速器：所有并发下载共享同一个实例，保证总速率不超过上限。
 *
 * 按"下一块数据最早可写时间"排队：每块数据占用 `bytes / rate` 的预算，
 * 调用方在预算未到之前等待，不会因为并发数变大而超速。
 *
 * @param bytesPerSecond 每秒允许的字节数；`<= 0` 表示不限速
 * @param clock 纳秒时钟，测试可注入
 * @param sleeper 等待函数，测试可注入（默认 `delay`）
 */
class RateLimiter(
    private val bytesPerSecond: Long,
    private val clock: () -> Long = System::nanoTime,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var nextFreeNanos = 0L

    /** 申请写入 [bytes] 个字节，必要时挂起等待。 */
    suspend fun acquire(bytes: Int) {
        if (bytesPerSecond <= 0 || bytes <= 0) return
        val waitMillis =
            mutex.withLock {
                val now = clock()
                val start = maxOf(now, nextFreeNanos)
                val budgetNanos = bytes.toLong() * NANOS_PER_SECOND / bytesPerSecond
                nextFreeNanos = start + budgetNanos
                val waitNanos = start - now
                if (waitNanos > 0) waitNanos / NANOS_PER_MILLI else 0L
            }
        if (waitMillis > 0) sleeper(waitMillis)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
