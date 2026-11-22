package dev.mmcl.app.loader

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.slf4j.Logger

/**
 * 加载器版本落盘：把插件返回的 overlay JSON 写到 `versions/<id>/<id>.json`。
 *
 * 只负责写盘与路径校验；下载、`inheritsFrom` 合并由 core 负责。
 *
 * @param versionsDir 通常是 `<dataDir>/versions`
 */
class LoaderInstallStore(
    private val versionsDir: Path,
    private val logger: Logger,
) {
    /**
     * 原子写入版本 JSON。
     *
     * @throws IOException JSON 为空或版本 id 非法时
     */
    fun save(versionId: String, detailJson: String): Path {
        if (detailJson.isBlank()) throw IOException("加载器返回的版本 JSON 为空")
        val safe =
            runCatching { Path.of(versionId.trim()).normalize() }.getOrElse {
                throw IOException("非法版本 id：$versionId", it)
            }
        if (safe.nameCount != 1 || safe.startsWith("..") || safe.isAbsolute) {
            throw IOException("非法版本 id：$versionId")
        }
        val dir = Files.createDirectories(versionsDir.resolve(safe))
        val target = dir.resolve("$versionId.json")
        val temp = Files.createTempFile(dir, "loader", ".tmp")
        try {
            Files.writeString(temp, detailJson, Charsets.UTF_8)
            runCatching {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { Files.deleteIfExists(temp) }
        }
        logger.info("加载器版本已写入：{}", target)
        return target
    }
}
