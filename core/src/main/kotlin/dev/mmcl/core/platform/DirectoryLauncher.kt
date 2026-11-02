package dev.mmcl.core.platform

import dev.mmcl.api.OperatingSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 在系统文件管理器里打开一个目录。
 *
 * 各平台走各自的标准入口，不经过 shell（`ARCHITECTURE.md` §12.1）：
 * macOS `open`、Windows `explorer.exe`、Linux 优先已知文件管理器，否则 `xdg-open`。
 *
 * Linux 不用裸 `xdg-open` 当唯一路径：在部分环境里 `xdg-mime` 认不出
 * `inode/directory`，会落到 `$BROWSER`，把目录当成网页打开。
 *
 * 只看进程是否起得来，不检查退出码 —— Windows 的 `explorer.exe` 即使成功打开也常返回 1。
 *
 * @param os 目标平台
 * @param start 起进程的方式，测试可注入
 * @param resolveLinuxFileManager Linux 上解析文件管理器可执行文件；测试可注入。默认按 PATH 找常见管理器。
 */
class DirectoryLauncher(
    private val os: OperatingSystem,
    private val start: (List<String>) -> Unit = { command -> ProcessBuilder(command).start() },
    private val resolveLinuxFileManager: () -> String? = Companion::findLinuxFileManager,
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
            OperatingSystem.LINUX -> {
                val manager = resolveLinuxFileManager()
                if (manager != null) listOf(manager, path) else listOf("xdg-open", path)
            }
        }
    }

    companion object {
        private val LINUX_FILE_MANAGERS =
            listOf("thunar", "nautilus", "dolphin", "pcmanfm", "nemo", "caja")

        /** 在 PATH 里找第一个可用的文件管理器；找不到返回 null，调用方再退回 xdg-open。 */
        fun findLinuxFileManager(): String? {
            val pathEnv = System.getenv("PATH") ?: return null
            for (name in LINUX_FILE_MANAGERS) {
                for (dir in pathEnv.split(PATH_SEPARATOR)) {
                    if (dir.isEmpty()) continue
                    val candidate = Paths.get(dir, name)
                    if (Files.isExecutable(candidate)) return candidate.toString()
                }
            }
            return null
        }

        private val PATH_SEPARATOR: String = System.getProperty("path.separator", ":")
    }
}
