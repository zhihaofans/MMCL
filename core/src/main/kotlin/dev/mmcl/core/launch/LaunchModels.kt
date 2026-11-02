package dev.mmcl.core.launch

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.Account
import dev.mmcl.core.version.VersionDetail
import java.nio.file.Path

/** 启动失败（参数构建或进程拉起）。 */
class LaunchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 一次启动请求。
 *
 * 账户信息由 `AuthProvider` 插件提供（离线账户为 `auth-offline`），core 不再内置离线占位。
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
    val auth: Account,
    val launcherName: String = "MMCL",
    val maxMemoryMb: Int = 2048,
    val extraJvmArgs: List<String> = emptyList(),
    /** 追加给游戏进程的环境变量（会覆盖同名系统变量）。 */
    val environment: Map<String, String> = emptyMap(),
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