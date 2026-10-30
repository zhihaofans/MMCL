package dev.mmcl.core.launch

import dev.mmcl.api.event.EventBus
import dev.mmcl.api.event.GameExited
import dev.mmcl.api.event.GameLaunching
import java.io.File
import java.nio.file.Path
import org.slf4j.Logger

/**
 * 启动游戏进程并跟踪生命周期。
 *
 * - 启动前发 `GameLaunching`，进程退出后发 `GameExited`（含退出码与时长）；
 * - 合并 stdout/stderr，逐行写日志并回调给界面；
 * - 每次启动一个守护线程负责读流与等待退出，不阻塞调用方。
 *
 * @param processStarter 进程创建函数，测试可注入假实现
 */
class MinecraftLauncher(
    private val events: EventBus,
    private val logger: Logger,
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
     * @param onLine 每行游戏输出回调（在监听线程里调用，UI 需自行切线程）
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

        events.publish(GameLaunching(instanceId = request.detail.id, versionId = request.detail.id))
        val startedAt = System.currentTimeMillis()
        Thread(
            { watch(process, request.detail.id, startedAt, onLine) },
            "mmcl-game-watch-${request.detail.id}",
        ).apply {
            isDaemon = true
            start()
        }
        return LaunchSession(process, command)
    }

    private fun watch(process: Process, versionId: String, startedAt: Long, onLine: ((String) -> Unit)?) {
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    logger.debug("[game] {}", line)
                    onLine?.invoke(line)
                }
            }
            val exitCode = process.waitFor()
            val duration = System.currentTimeMillis() - startedAt
            logger.info("游戏 {} 已退出：code={}，用时 {} ms", versionId, exitCode, duration)
            events.publish(GameExited(instanceId = versionId, exitCode = exitCode, durationMillis = duration))
        } catch (t: Throwable) {
            logger.error("监听游戏进程失败：{}", versionId, t)
        }
    }
}

/** 一次运行中的会话，界面用它显示 pid / 停止游戏。 */
class LaunchSession(
    val process: Process,
    val command: LaunchCommand,
) {
    /** 进程 pid。 */
    val pid: Long get() = process.pid()

    /** 进程是否还在运行。 */
    val isRunning: Boolean get() = process.isAlive

    /** 请求结束进程（正常关闭游戏窗口）。 */
    fun stop() {
        process.destroy()
    }
}
