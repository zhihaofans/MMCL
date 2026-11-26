package dev.mmcl.app.plugin

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.slf4j.Logger

/**
 * 把用户选择的插件 JAR 安装到 `<dataDir>/plugins`。
 *
 * 只做本地文件复制与基础校验；插件在下次启动时由 `PluginManager` 扫描并校验签名/冲突。
 * 同名文件会被原子替换（升级同一插件的常见做法）。
 */
class PluginInstaller(
    private val pluginsDir: Path,
    private val logger: Logger,
) {
    /**
     * 安装一个 JAR。
     *
     * @return 目标文件
     * @throws IOException 源文件不存在、不是 `.jar` 或复制失败
     */
    fun install(source: Path): Result<Path> =
        runCatching {
            if (!Files.isRegularFile(source)) throw IOException("文件不存在：$source")
            val name = source.fileName.toString()
            if (!name.endsWith(".jar", ignoreCase = true)) throw IOException("不是 .jar 文件：$name")

            Files.createDirectories(pluginsDir)
            val target = pluginsDir.resolve(name).normalize()
            if (!target.startsWith(pluginsDir.toAbsolutePath().normalize()) && !target.startsWith(pluginsDir.normalize())) {
                throw IOException("非法文件名：$name")
            }
            val temp = Files.createTempFile(pluginsDir, "plugin", ".tmp")
            try {
                Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING)
                runCatching {
                    Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                }.getOrElse {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                runCatching { Files.deleteIfExists(temp) }
            }
            logger.info("已安装插件 JAR：{} -> {}", source, target)
            target
        }
}
