package dev.mmcl.core.download

import dev.mmcl.core.net.HttpTransport
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.Logger

/** 下载进度。 */
data class DownloadProgress(
    val completed: Int,
    val total: Int,
    val current: String = "",
    /** 阶段标识：`files`（游戏文件）/ `assets`（资源对象）。 */
    val phase: String = "",
)

/** 单个文件失败原因。 */
data class DownloadFailure(
    val file: PlannedFile,
    val error: Throwable,
)

/** 一次下载的汇总。 */
data class DownloadReport(
    val downloaded: Int,
    val skipped: Int,
    val failures: List<DownloadFailure>,
) {
    /** 是否全部成功。 */
    val isSuccess: Boolean get() = failures.isEmpty()
}

/**
 * 并发下载引擎：下载 → 校验 SHA-1 → 原子落盘。
 *
 * 约定：
 * - URL 全部来自 [PlannedFile]（也就是清单/插件提供的地址），引擎本身不含任何地址；
 * - 已存在且校验通过的文件跳过，支持断点续跑；
 * - 先写 `*.mmcl-tmp`，校验通过才 `move` 到目标位置，校验失败会删除临时文件；
 * - 单个文件失败不影响其它文件，统一收集到 [DownloadReport.failures]；
 * - 相对路径必须是游戏根目录内的普通相对路径，`..` / 绝对路径会被拒绝（防路径穿越）。
 *
 * @param transport 传输层，测试可注入
 * @param logger 运行日志
 * @param concurrency 并发数
 * @param maxBytesPerSecond 全局速率上限（字节/秒，0 表示不限）；每次下载开始时读取，支持运行时改设置
 * @param maxAttempts 单文件最多尝试次数（含首次）
 * @param retryDelayMillis 第 n 次失败后的等待时间；测试可注入 0
 */
class FileDownloader(
    private val transport: HttpTransport,
    private val logger: Logger,
    private val concurrency: Int = 6,
    private val maxBytesPerSecond: () -> Long = { 0 },
    private val maxAttempts: Int = 3,
    private val retryDelayMillis: (attempt: Int) -> Long = { attempt -> 500L shl (attempt - 1) },
) {
    /**
     * 下载全部文件。
     *
     * @param root 游戏根目录（`<dataDir>`）
     * @param files 规划好的文件清单
     * @param onProgress 每完成一个文件回调一次，用于界面进度
     */
    private class ChecksumMismatchException(message: String) : IOException(message)

    private val HTTP_STATUS = Regex("HTTP (\\d{3})")

    suspend fun download(
        root: Path,
        files: List<PlannedFile>,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): DownloadReport =
        withContext(Dispatchers.IO) {
            val rootPath = root.toAbsolutePath().normalize()
            val total = files.size
            val completed = AtomicInteger(0)
            val downloaded = AtomicInteger(0)
            val skipped = AtomicInteger(0)
            val failures = Collections.synchronizedList(mutableListOf<DownloadFailure>())
            val permits = Semaphore(concurrency.coerceAtLeast(1))
            // 所有并发文件共享一个限速器，避免"并发 6 个就是 6 倍速"
            val limiter = RateLimiter(maxBytesPerSecond())
            // 完成计数与进度回调串行化，避免并发下载时进度"倒退"（出现过 [2,1]）
            val progressLock = Mutex()

            coroutineScope {
                files
                    .map { file ->
                        async {
                            permits.withPermit {
                                try {
                                    val target = resolveTarget(rootPath, file)
                                    if (target == null) {
                                        throw IOException("非法路径：${file.relativePath}")
                                    }
                                    if (isUpToDate(target, file)) {
                                        skipped.incrementAndGet()
                                        logger.debug("已存在且校验通过，跳过：{}", file.relativePath)
                                    } else {
                                        fetch(rootPath, target, file, limiter)
                                        downloaded.incrementAndGet()
                                    }
                                } catch (t: CancellationException) {
                                    throw t
                                } catch (t: Throwable) {
                                    failures += DownloadFailure(file, t)
                                    logger.warn("下载失败：{}（{}）", file.relativePath, t.message)
                                } finally {
                                    progressLock.withLock {
                                        val done = completed.incrementAndGet()
                                        onProgress(
                                            DownloadProgress(completed = done, total = total, current = file.relativePath),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .awaitAll()
            }

            DownloadReport(
                downloaded = downloaded.get(),
                skipped = skipped.get(),
                failures = failures.toList(),
            )
        }

    private suspend fun fetch(root: Path, target: Path, file: PlannedFile, limiter: RateLimiter) {
        val parent = target.parent
        if (parent != null) Files.createDirectories(parent)
        val temp = parent.resolve("${target.fileName}.mmcl-tmp")

        var lastError: Throwable? = null
        var attempts = 0
        for (attempt in 1..maxAttempts.coerceAtLeast(1)) {
            attempts = attempt
            try {
                attemptFetch(temp, target, file, limiter)
                logger.debug("已下载并校验：{}（root={}，尝试 {} 次）", file.relativePath, root.fileName, attempt)
                return
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                lastError = t
                if (!isRetryable(t) || attempt >= maxAttempts) break
                val wait = retryDelayMillis(attempt)
                logger.warn(
                    "下载失败，{} ms 后重试（{}/{}）：{}（{}）",
                    wait,
                    attempt,
                    maxAttempts,
                    file.relativePath,
                    t.message,
                )
                delay(wait)
            }
        }
        throw IOException(
            "下载失败：${file.relativePath}（已尝试 $attempts 次）：${lastError?.message}",
            lastError,
        )
    }

    private suspend fun attemptFetch(
        temp: Path,
        target: Path,
        file: PlannedFile,
        limiter: RateLimiter,
    ) {
        Files.deleteIfExists(temp)
        try {
            transport.downloadTo(file.url, temp) { bytes -> limiter.acquire(bytes) }
            if (file.sha1.isNotBlank() && !Sha1.matches(temp, file.sha1)) {
                throw ChecksumMismatchException("SHA-1 校验失败：${file.relativePath}")
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(temp) }
            throw t
        }
    }

    /** 404/403 这类错误重试没意义；网络错误、429、5xx 才重试。 */
    private fun isRetryable(t: Throwable): Boolean {
        // 内容损坏重试也不会变好
        if (t is ChecksumMismatchException) return false
        if (t !is IOException) return false
        val status = HTTP_STATUS.findAll(t.message.orEmpty()).firstOrNull()?.groupValues?.get(1)?.toIntOrNull()
        return status == null || status == 429 || status >= 500
    }

    private fun isUpToDate(target: Path, file: PlannedFile): Boolean {
        if (!Files.isRegularFile(target)) return false
        if (file.sha1.isNotBlank()) return Sha1.matches(target, file.sha1)
        if (file.size > 0) {
            return runCatching { Files.size(target) == file.size }.getOrDefault(false)
        }
        return false
    }

    private fun resolveTarget(root: Path, file: PlannedFile): Path? {
        val relative =
            runCatching { Path.of(file.relativePath).normalize() }.getOrElse {
                logger.warn("非法相对路径：{}", file.relativePath)
                return null
            }
        if (relative.isAbsolute || relative.startsWith("..")) {
            logger.warn("拒绝穿越游戏目录的路径：{}", file.relativePath)
            return null
        }
        val target = root.resolve(relative).normalize()
        return if (target.startsWith(root)) target else null
    }
}
