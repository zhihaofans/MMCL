package dev.mmcl.core.platform

import dev.mmcl.api.OperatingSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * 在系统文件管理器里打开一个目录。
 *
 * 各平台走各自的标准入口，不经过 shell（`ARCHITECTURE.md` §12.1）：
 * macOS `open`、Windows `explorer.exe`、Linux `xdg-open`。
 *
 * 只看进程是否起得来，不检查退出码 —— Windows 的 `explorer.exe` 即使成功打开也常返回 1。
 *
 * @param os 目标平台
 * @param start 起进程的方式，测试可注入
 */
class DirectoryLauncher(
    private val os: OperatingSystem,
    private val start: (List<String>) -> Unit = { command -> ProcessBuilder(command).start() },
) {
    /**
     * 确保目录存在后打开它。
     *
     * @return 成功或失败原因；失败不应向上抛，界面把原因显示出来即可
     */
    fun open(dir: Path): Result<Unit> =
        runCatching {
            val target = dir.toAbsolutePath().normalize()
            Files.createDirectories(target)
            start(command(target))
        }

    /** 按平台构造打开目录的命令；单独暴露便于单元测试。 */
    fun command(dir: Path): List<String> {
        val path = dir.toString()
        return when (os) {
            OperatingSystem.MACOS -> listOf("open", path)
            OperatingSystem.WINDOWS -> listOf("explorer.exe", path)
            OperatingSystem.LINUX -> listOf("xdg-open", path)
        }
    }
}
