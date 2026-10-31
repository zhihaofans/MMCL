package dev.mmcl.core.launch

import dev.mmcl.api.event.EventBus
import dev.mmcl.api.event.GameExited
import dev.mmcl.api.event.GameLaunching
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.slf4j.Logger

/**
 * 启动游戏进程并跟踪生命周期。
 *
 * - 启动前发 `GameLaunching`，进程退出后发 `GameExited`（含退出码与时长）；
 * - 合并 stdout/stderr：写日志、写 `latest.log`、推给 [LaunchSession.output]，并回调 `onLine`；
 * - 每次启动一个守护线程负责读流与等待退出，不阻塞调用方。
 *
 * @param logFile 根据版本 id 给出完整日志落盘路径；返回 `null` 表示不落盘。测试可注入
 * @param processStarter 进程创建函数，测试可注入假实现
 */
class MinecraftLauncher(
    private val events: EventBus,
    private val logger: Logger,
    private val logFile: (versionId: String) -> Path? = { null },
    private val processStarter: (List<String>, Path) -> Process = { command, directory ->
        ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start()
    },
) {
    /**
     * 启动游戏。
     *
     * @param onLine 每行游戏输出回调（在监听线程里调用，UI 应改用 [LaunchSession.output]）
     * @throws LaunchException 参数构建失败或进程拉不起来
     */
    fun launch(request: LaunchRequest, onLine: ((String) -> Unit)? = null): LaunchSession {
        val command = LaunchCommandBuilder.build(request)
        val classpathEntries = command.classpath.split(File.pathSeparatorChar).count { it.isNotBlank() }
        logger.info(
            "启动游戏 {}：java={}，classpath {} 项",
            request.detail.id,
            request.javaExecutable,
            classpathEntries,
        )
        val process =
            try {
                processStarter(command.asProcessCommand(), request.instanceDir)
            } catch (t: Throwable) {
                throw LaunchException("启动游戏进程失败：${request.detail.id}", t)
            }

        val logPath = runCatching { logFile(request.detail.id) }.getOrNull()
        val writer = openLogWriter(logPath)
        val outputFlow = MutableSharedFlow<String>(extraBufferCapacity = OUTPUT_BUFFER)
        val formatter = GameLogFormatter()
        val session =
            LaunchSession(
                process = process,
                command = command,
                outputFlow = outputFlow,
                logFile = logPath,
            )

        events.publish(GameLaunching(instanceId = request.detail.id, versionId = request.detail.id))
        val startedAt = System.currentTimeMillis()
        Thread(
            { watch(process, session, request.detail.id, startedAt, onLine, writer, formatter) },
            "mmcl-game-watch-${request.detail.id}",
        ).apply {
            isDaemon = true
            start()
        }
        return session
    }

    private fun watch(
        process: Process,
        session: LaunchSession,
        versionId: String,
        startedAt: Long,
        onLine: ((String) -> Unit)?,
        writer: BufferedWriter?,
        formatter: GameLogFormatter,
    ) {
        try {
            var flushed = 0
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    logger.debug("[game] {}", line)
                    // 界面与缓冲用可读行；文件保留原始 XML
                    for (formatted in formatter.accept(line)) {
                        session.append(formatted)
                        session.emit(formatted)
                        onLine?.invoke(formatted)
                    }
                    if (writer != null) {
                        runCatching {
                            writer.append(line).append('\n')
                            if (++flushed % 32 == 0) writer.flush()
                        }.onFailure { logger.warn("写游戏日志失败：{}", it.message) }
                    }
                }
            }
            for (rest in formatter.finish()) {
                session.append(rest)
                session.emit(rest)
                onLine?.invoke(rest)
            }
            val exitCode = process.waitFor()
            val duration = System.currentTimeMillis() - startedAt
            runCatching { writer?.flush() }
            logger.info("游戏 {} 已退出：code={}，用时 {} ms", versionId, exitCode, duration)
            events.publish(GameExited(instanceId = versionId, exitCode = exitCode, durationMillis = duration))
        } catch (t: Throwable) {
            logger.error("监听游戏进程失败：{}", versionId, t)
        } finally {
            runCatching { writer?.flush() }
            runCatching { writer?.close() }
        }
    }

    private fun openLogWriter(path: Path?): BufferedWriter? {
        if (path == null) return null
        return runCatching {
            val absolute = path.toAbsolutePath()
            absolute.parent?.let { Files.createDirectories(it) }
            Files.newBufferedWriter(
                absolute,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }.onFailure { logger.warn("打开游戏日志文件失败（{}）：{}", path, it.message) }.getOrNull()
    }

    companion object {
        private const val OUTPUT_BUFFER = 1024
    }
}

/**
 * 一次运行中的会话，界面用它显示 pid / 日志 / 停止游戏。
 *
 * 日志同时保留在环形缓冲里（最多 [MAX_LINES] 行），供界面在订阅前补齐历史。
 */
class LaunchSession(
    val process: Process,
    val command: LaunchCommand,
    private val outputFlow: MutableSharedFlow<String>,
    /** 完整日志落盘路径；未启用时为 `null`。 */
    val logFile: Path? = null,
) {
    /** 逐行输出的热流；没有订阅者时写入会被丢弃，历史请用 [snapshot]。 */
    val output: SharedFlow<String> get() = outputFlow
    private val lock = Any()
    private val buffer = ArrayDeque<String>()

    /** 进程 pid。 */
    val pid: Long get() = process.pid()

    /** 进程是否还在运行。 */
    val isRunning: Boolean get() = process.isAlive

    /** 当前缓冲里的日志快照（最多 [MAX_LINES] 行）。 */
    fun snapshot(): List<String> = synchronized(lock) { buffer.toList() }

    internal fun append(line: String) {
        synchronized(lock) {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(line)
        }
    }

    internal fun emit(line: String) {
        outputFlow.tryEmit(line)
    }

    /** 请求结束进程（正常关闭游戏窗口）。 */
    fun stop() {
        process.destroy()
    }

    companion object {
        /** 环形缓冲上限。 */
        const val MAX_LINES: Int = 500
    }
}
