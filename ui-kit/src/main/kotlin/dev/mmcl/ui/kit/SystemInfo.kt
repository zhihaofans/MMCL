package dev.mmcl.ui.kit

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path

/** 本机基本硬件信息。`memoryMb` 为 0 表示读取不到。 */
data class SystemProfile(
    val osName: String,
    val osVersion: String,
    val arch: String,
    val cpuCores: Int,
    val memoryMb: Long,
)

/** 环境检查提示。 */
enum class SystemNote {
    /** 内存低于官方最低（独显 8 GB / 核显 12 GB）。 */
    LOW_MEMORY,

    /** 内存介于 8–12 GB：核显环境低于官方最低。 */
    INTEGRATED_MAY_NEED_MORE,

    /** CPU 少于 4 物理核。 */
    LOW_CORES,

    /** 读不到物理内存。 */
    UNKNOWN_MEMORY,
}

/** 环境检查结果。 */
data class SystemCheck(
    val profile: SystemProfile,
    val notes: List<SystemNote>,
) {
    val belowMinimum: Boolean
        get() = SystemNote.LOW_MEMORY in notes || SystemNote.LOW_CORES in notes
}

/**
 * 读取本机信息并对照 Minecraft 26.x 的官方最低要求（2026-07 更新）：
 * 64 位系统、4 核、8 GB（独显）/ 12 GB（核显）、Vulkan 1.3 GPU。
 *
 * 只做提示，不阻止启动：官方说明低于最低配置仍可能启动，但性能不保证。
 */
object SystemInfo {
    private const val MIN_MEMORY_MB = 8L * 1024
    private const val INTEGRATED_MEMORY_MB = 12L * 1024
    private const val MIN_CORES = 4
    private const val BYTES_PER_MB = 1024L * 1024L

    fun profile(): SystemProfile =
        SystemProfile(
            osName = System.getProperty("os.name").orEmpty(),
            osVersion = System.getProperty("os.version").orEmpty(),
            arch = System.getProperty("os.arch").orEmpty(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            memoryMb = physicalMemoryMb(),
        )

    fun evaluate(profile: SystemProfile): SystemCheck {
        val notes = mutableListOf<SystemNote>()
        when {
            profile.memoryMb <= 0 -> notes += SystemNote.UNKNOWN_MEMORY
            profile.memoryMb < MIN_MEMORY_MB -> notes += SystemNote.LOW_MEMORY
            profile.memoryMb < INTEGRATED_MEMORY_MB -> notes += SystemNote.INTEGRATED_MAY_NEED_MORE
        }
        if (profile.cpuCores in 1 until MIN_CORES) notes += SystemNote.LOW_CORES
        return SystemCheck(profile, notes)
    }

    /** 通过公开接口读物理内存；失败时回退到 macOS `sysctl` 或 Linux `/proc/meminfo`。 */
    private fun physicalMemoryMb(): Long {
        val fromBean =
            runCatching {
                val bean = ManagementFactory.getOperatingSystemMXBean()
                val type = Class.forName("com.sun.management.OperatingSystemMXBean")
                if (!type.isInstance(bean)) return@runCatching 0L
                val bytes = type.getMethod("getTotalMemorySize").invoke(bean) as? Long ?: return@runCatching 0L
                bytes / BYTES_PER_MB
            }.getOrDefault(0L)
        if (fromBean > 0) return fromBean

        val os = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            os.contains("mac") -> runCommand("sysctl", "-n", "hw.memsize") / BYTES_PER_MB
            os.contains("linux") -> linuxMemoryMb()
            else -> 0
        }
    }

    private fun linuxMemoryMb(): Long =
        runCatching {
            val line = Files.readAllLines(Path.of("/proc/meminfo")).firstOrNull { it.startsWith("MemTotal:") }
                ?: return 0
            val kb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: return 0
            kb / 1024
        }.getOrDefault(0)

    private fun runCommand(vararg command: String): Long =
        runCatching {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return 0
            }
            text.trim().toLongOrNull() ?: 0L
        }.getOrDefault(0L)
}
