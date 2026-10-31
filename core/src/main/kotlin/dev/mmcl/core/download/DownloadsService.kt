package dev.mmcl.core.download

import dev.mmcl.core.version.VersionManifestEntry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.Logger

/** 一个下载任务的状态。 */
enum class DownloadTaskState {
    /** 排队中。 */
    QUEUED,

    /** 正在规划文件。 */
    PLANNING,

    /** 正在下载。 */
    DOWNLOADING,

    /** 已完成（可能全部跳过）。 */
    COMPLETED,

    /** 失败。 */
    FAILED,

    /** 已取消。 */
    CANCELLED,
    ;

    val isActive: Boolean get() = this == QUEUED || this == PLANNING || this == DOWNLOADING
}

/**
 * 一个版本的下载任务快照。
 *
 * @property versionId 版本 id，也是任务去重键
 * @property progress 最新进度；不在下载阶段时为 `null`
 * @property result 完成后的结果
 * @property error 失败原因
 */
data class DownloadTask(
    val versionId: String,
    val state: DownloadTaskState,
    val progress: DownloadProgress? = null,
    val result: InstallResult? = null,
    val error: String? = null,
)

/**
 * 后台下载队列：页面只订阅 [tasks]，任务本身跑在应用级作用域里。
 *
 * - **串行**执行（一次只装一个版本），避免多个安装同时写共享目录；
 * - 同一版本去重：已有排队/进行中的任务时直接返回；
 * - 切页不取消：任务属于构造时传入的 scope，不依赖 Compose 生命周期。
 *
 * @param installer 实际安装实现（生产为 `GameInstaller`）
 * @param scope 应用级作用域，窗口生命周期内有效
 */
class DownloadsService(
    private val installer: Installer,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val lock = Any()
    private val tasksById = LinkedHashMap<String, DownloadTask>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val queue = Channel<QueuedTask>(Channel.UNLIMITED)
    private val cancelled = mutableSetOf<String>()

    private val mutableTasks = MutableStateFlow<List<DownloadTask>>(emptyList())

    /** 当前所有任务快照（按入队顺序）。 */
    val tasks: StateFlow<List<DownloadTask>> = mutableTasks.asStateFlow()

    init {
        scope.launch {
            for (queued in queue) {
                val job = scope.launch { runTask(queued.entry, queued.forceRefresh) }
                jobs[queued.entry.id] = job
                job.join()
            }
        }
    }

    private data class QueuedTask(
        val entry: VersionManifestEntry,
        val forceRefresh: Boolean,
    )

    /**
     * 入队一个版本的安装。
     *
     * @return 当前任务快照；同版本已有活动任务时返回已有的
     */
    fun enqueue(entry: VersionManifestEntry, forceRefresh: Boolean = false): DownloadTask {
        synchronized(lock) {
            val existing = tasksById[entry.id]
            if (existing?.state?.isActive == true) return existing
            cancelled -= entry.id
            val task = DownloadTask(versionId = entry.id, state = DownloadTaskState.QUEUED)
            tasksById[entry.id] = task
            publish()
            queue.trySend(QueuedTask(entry, forceRefresh))
            return task
        }
    }

    /** 取消排队中或进行中的任务。 */
    fun cancel(versionId: String) {
        synchronized(lock) {
            val task = tasksById[versionId] ?: return
            if (!task.state.isActive) return
            cancelled += versionId
            tasksById[versionId] = task.copy(state = DownloadTaskState.CANCELLED, progress = null)
            publish()
        }
        jobs.remove(versionId)?.cancel()
        logger.info("已请求取消下载：{}", versionId)
    }

    private suspend fun runTask(entry: VersionManifestEntry, forceRefresh: Boolean) {
        synchronized(lock) {
            if (entry.id in cancelled) {
                cancelled -= entry.id
                jobs.remove(entry.id)
                return
            }
            update(entry.id) { it.copy(state = DownloadTaskState.PLANNING) }
        }
        try {
            val result =
                installer.install(entry, forceRefresh = forceRefresh) { progress ->
                    update(entry.id) { it.copy(state = DownloadTaskState.DOWNLOADING, progress = progress) }
                }
            update(entry.id) { it.copy(state = DownloadTaskState.COMPLETED, progress = null, result = result) }
            logger.info("下载任务完成：{}", entry.id)
        } catch (t: CancellationException) {
            update(entry.id) { it.copy(state = DownloadTaskState.CANCELLED, progress = null) }
            logger.info("下载任务已取消：{}", entry.id)
            throw t
        } catch (t: Throwable) {
            logger.warn("下载任务失败：{}（{}）", entry.id, t.message)
            update(entry.id) { it.copy(state = DownloadTaskState.FAILED, progress = null, error = t.message ?: t::class.simpleName) }
        } finally {
            jobs.remove(entry.id)
            synchronized(lock) { cancelled -= entry.id }
        }
    }

    private fun update(versionId: String, transform: (DownloadTask) -> DownloadTask) {
        synchronized(lock) {
            val current = tasksById[versionId] ?: return
            tasksById[versionId] = transform(current)
            publish()
        }
    }

    private fun publish() {
        mutableTasks.value = tasksById.values.toList()
    }
}
