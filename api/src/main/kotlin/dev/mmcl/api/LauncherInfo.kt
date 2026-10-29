package dev.mmcl.api

import java.nio.file.Path

/** 启动器运行所在的桌面操作系统。 */
enum class OperatingSystem {
    WINDOWS,
    MACOS,
    LINUX,
}

/** 进程所在的 CPU 架构，仅用于选择运行时产物，不要用来做业务分支。 */
enum class Architecture {
    ARM64,
    X86_64,
    UNKNOWN,
}

/**
 * 只读的启动器信息，注入到每个 [dev.mmcl.api.plugin.PluginContext]。
 *
 * @property name 启动器显示名
 * @property version 启动器版本，唯一来源是构建时的 `gradle.properties`
 * @property os 当前操作系统，由 core 探测后注入
 * @property arch 当前 CPU 架构，由 core 探测后注入
 * @property dataDir 跨平台数据目录（macOS 为 `~/Library/Application Support/MMCL`）
 */
data class LauncherInfo(
    val name: String,
    val version: String,
    val os: OperatingSystem,
    val arch: Architecture,
    val dataDir: Path,
)
