package dev.mmcl.core.platform

import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.OperatingSystem
import java.nio.file.Path

/**
 * 平台探测与跨平台路径计算的唯一入口。
 *
 * 业务代码里禁止散落 `os.name` 判断，也禁止硬编码 `/Users/`、`C:\` 等路径（见 `AGENTS.md` §3）。
 * 所有方法都把宿主相关输入作为参数暴露，方便单元测试直接验证三平台分支。
 */
object Platforms {
    /** macOS / Windows 使用大写目录名，Linux 沿用 XDG 的小写习惯。 */
    const val APP_DIR_NAME_MAC_WIN: String = "MMCL"

    /** Linux 下 XDG 数据目录里的子目录名。 */
    const val APP_DIR_NAME_LINUX: String = "mmcl"

    /**
     * 由 `os.name` 推断操作系统。
     *
     * @param osName JVM 的 `os.name` 属性值
     * @return 未知系统一律按 [OperatingSystem.LINUX] 处理，调用方如有需要可自行记录告警
     */
    fun currentOs(osName: String = System.getProperty("os.name").orEmpty()): OperatingSystem {
        val normalized = osName.lowercase()
        return when {
            normalized.contains("win") -> OperatingSystem.WINDOWS
            normalized.contains("mac") || normalized.contains("darwin") -> OperatingSystem.MACOS
            else -> OperatingSystem.LINUX
        }
    }

    /**
     * 由 `os.arch` 推断 CPU 架构。
     *
     * @param archName JVM 的 `os.arch` 属性值
     */
    fun currentArch(archName: String = System.getProperty("os.arch").orEmpty()): Architecture =
        when (archName.lowercase()) {
            "aarch64", "arm64" -> Architecture.ARM64
            "x86_64", "amd64" -> Architecture.X86_64
            else -> Architecture.UNKNOWN
        }

    /**
     * 计算各平台的默认数据目录。
     *
     * | 平台 | 结果 |
     * |---|---|
     * | macOS | `~/Library/Application Support/MMCL` |
     * | Windows | `%APPDATA%\MMCL`（缺失时回落 `~/AppData/Roaming/MMCL`） |
     * | Linux | `$XDG_DATA_HOME/mmcl`（缺失时回落 `~/.local/share/mmcl`） |
     *
     * @param os 目标平台
     * @param home 用户主目录，测试时可注入
     * @param env 环境变量读取函数，测试时可注入
     */
    fun dataDir(
        os: OperatingSystem,
        home: Path = defaultHome(),
        env: (String) -> String? = { System.getenv(it) },
    ): Path =
        when (os) {
            OperatingSystem.MACOS ->
                home.resolve("Library").resolve("Application Support").resolve(APP_DIR_NAME_MAC_WIN)

            OperatingSystem.WINDOWS ->
                baseDir(env("APPDATA"), home.resolve("AppData").resolve("Roaming"))
                    .resolve(APP_DIR_NAME_MAC_WIN)

            OperatingSystem.LINUX ->
                baseDir(env("XDG_DATA_HOME"), home.resolve(".local").resolve("share"))
                    .resolve(APP_DIR_NAME_LINUX)
        }

    /**
     * 组装注入给插件的 [LauncherInfo]。
     *
     * @param version 启动器版本，由构建产物提供，不要在运行时另算
     */
    fun launcherInfo(
        version: String,
        name: String = "MMCL",
        os: OperatingSystem = currentOs(),
        arch: Architecture = currentArch(),
        home: Path = defaultHome(),
        env: (String) -> String? = { System.getenv(it) },
    ): LauncherInfo =
        LauncherInfo(
            name = name,
            version = version,
            os = os,
            arch = arch,
            dataDir = dataDir(os, home, env),
        )

    private fun baseDir(fromEnv: String?, fallback: Path): Path =
        fromEnv?.takeIf { it.isNotBlank() }?.let(Path::of) ?: fallback

    private fun defaultHome(): Path {
        val home = System.getProperty("user.home")
        return if (home.isNullOrBlank()) Path.of(".") else Path.of(home)
    }
}
