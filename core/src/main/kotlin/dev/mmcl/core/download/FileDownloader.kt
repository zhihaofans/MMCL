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
 */
class FileDownloader(
    private val transport: HttpTransport,
    private val logger: Logger,
    private val concurrency: Int = 6,
) {
    /**
     * 下载全部文件。
     *
     * @param root 游戏根目录（`<dataDir>`）
     * @param files 规划好的文件清单
     * @param onProgress 每完成一个文件回调一次，用于界面进度
     */
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
                                        fetch(rootPath, target, file)
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

    private suspend fun fetch(root: Path, target: Path, file: PlannedFile) {
        val parent = target.parent
        if (parent != null) Files.createDirectories(parent)
        val temp = parent.resolve("${target.fileName}.mmcl-tmp")
        Files.deleteIfExists(temp)
        try {
            transport.downloadTo(file.url, temp)
            if (file.sha1.isNotBlank() && !Sha1.matches(temp, file.sha1)) {
                throw IOException("SHA-1 校验失败：${file.relativePath}")
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(temp) }
            throw t
        }
        logger.debug("已下载并校验：{}（root={}）", file.relativePath, root.fileName)
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
