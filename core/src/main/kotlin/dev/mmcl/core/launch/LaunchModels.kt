package dev.mmcl.core.launch

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.version.VersionDetail
import java.nio.file.Path
import java.util.UUID

/** 启动失败（参数构建或进程拉起）。 */
class LaunchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 一次启动请求。
 *
 * 账户目前是**离线占位**：名字来自设置（默认 `Player`），UUID 用官方离线算法生成，
 * accessToken 固定 `0`。等 `auth-*` 插件落地后替换 [playerName] 这段即可。
 *
 * @param sharedRoot 共享根（`<dataDir>`）：libraries / assets / versions 都放这里
 * @param instanceDir 实例的游戏目录（`game_directory`，存档/配置在实例里）
 * @param javaExecutable 由 `JavaRuntimeDetector` 挑出的 java
 */
data class LaunchRequest(
    val detail: VersionDetail,
    val sharedRoot: Path,
    val instanceDir: Path,
    val javaExecutable: Path,
    val launcherVersion: String,
    val os: OperatingSystem,
    val arch: Architecture,
    val playerName: String = "Player",
    val launcherName: String = "MMCL",
    val maxMemoryMb: Int = 2048,
    val extraJvmArgs: List<String> = emptyList(),
)

/** 构建好的启动命令。 */
data class LaunchCommand(
    val executable: Path,
    val arguments: List<String>,
    val classpath: String,
) {
    /** 传给 `ProcessBuilder` 的完整命令。 */
    fun asProcessCommand(): List<String> = listOf(executable.toString()) + arguments
}

/** 官方约定的离线 UUID：`UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`。 */
fun offlineUuid(playerName: String): String =
    UUID.nameUUIDFromBytes("OfflinePlayer:$playerName".toByteArray(Charsets.UTF_8)).toString()
