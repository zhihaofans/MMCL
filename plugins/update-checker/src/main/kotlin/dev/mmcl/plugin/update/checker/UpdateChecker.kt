package dev.mmcl.plugin.update.checker

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger

/**
 * 检查启动器与插件的更新。
 *
 * 不调用 GitHub API：`releases/latest` 的 302 得到 tag，再取 Release 里的
 * `update-index.json`（由 CI 生成）。索引缺失（旧版本 Release）时仍可判断启动器是否有新版。
 */
internal class UpdateChecker(
    private val http: UpdateHttp,
    val currentVersion: String,
    private val pluginsDir: Path,
    private val repoBase: String = "https://github.com/zhihaofans/MMCL",
    private val logger: Logger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateCheckResult {
        val tag =
            http.latestTag("$repoBase/releases/latest")
                ?: throw IOException("无法解析最新版本（releases/latest 没有跳到 tag）")
        val version = tag.removePrefix("v")
        val index =
            runCatching {
                json.decodeFromString<UpdateIndexDto>(
                    http.getText("$repoBase/releases/download/$tag/update-index.json"),
                )
            }.getOrElse {
                logger.info("更新索引不可用（{}），只检查启动器版本", it.message)
                null
            }
        val installed = installedPlugins()
        val plugins =
            index?.plugins.orEmpty().mapNotNull { remote ->
                val id = remote.id.trim()
                val remoteVersion = remote.version.trim()
                if (id.isEmpty() || remoteVersion.isEmpty() || remote.file.isBlank()) return@mapNotNull null
                val local = installed[id]
                if (local == null || compareVersions(remoteVersion, local.version) > 0) {
                    PluginState.UpdateAvailable(local, remote)
                } else {
                    PluginState.UpToDate(local, remote)
                }
            }
        return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = version,
            latestTag = tag,
            releaseUrl = index?.launcher?.releaseUrl?.takeIf { it.isNotBlank() } ?: "$repoBase/releases/tag/$tag",
            indexAvailable = index != null,
            plugins = plugins,
        )
    }

    /** 下载并安装一个插件版本；校验 SHA-256，成功后删除同 id 的旧 JAR。 */
    suspend fun install(remote: PluginDto, tag: String): Result<Path> =
        runCatching {
            if (!remote.file.endsWith(".jar", ignoreCase = true)) throw IOException("不是 JAR：${remote.file}")
            Files.createDirectories(pluginsDir)
            val target = pluginsDir.resolve(remote.file).normalize()
            if (!target.startsWith(pluginsDir.toAbsolutePath().normalize()) && !target.startsWith(pluginsDir.normalize())) {
                throw IOException("非法文件名：${remote.file}")
            }
            val temp = Files.createTempFile(pluginsDir, "update", ".tmp")
            try {
                http.download("$repoBase/releases/download/$tag/${remote.file}", temp)
                val actual = sha256(temp)
                if (remote.sha256.isNotBlank() && !actual.equals(remote.sha256, ignoreCase = true)) {
                    throw IOException("SHA-256 校验失败：${remote.file}")
                }
                runCatching {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }.getOrElse {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                runCatching { Files.deleteIfExists(temp) }
            }
            removeOlderVersions(remote.id, target)
            logger.info("插件更新完成：{} -> {}", remote.id, target.fileName)
            target
        }

    private fun installedPlugins(): Map<String, InstalledPlugin> {
        if (!Files.isDirectory(pluginsDir)) return emptyMap()
        val result = mutableMapOf<String, InstalledPlugin>()
        try {
            Files.list(pluginsDir).use { stream ->
                for (file in stream) {
                    if (!Files.isRegularFile(file)) continue
                    if (!file.fileName.toString().endsWith(".jar", ignoreCase = true)) continue
                    val info = pluginFileInfo(file.fileName.toString()) ?: continue
                    result[info.id] = info
                }
            }
        } catch (e: IOException) {
            logger.warn("读取插件目录失败：{}", e.message)
        }
        return result
    }

    private fun removeOlderVersions(id: String, keep: Path) {
        runCatching {
            Files.list(pluginsDir).use { stream ->
                for (file in stream) {
                    if (file == keep || !Files.isRegularFile(file)) continue
                    val info = pluginFileInfo(file.fileName.toString()) ?: continue
                    if (info.id == id) {
                        runCatching { Files.deleteIfExists(file) }
                            .onSuccess { logger.info("已删除旧版本插件：{}", file.fileName) }
                    }
                }
            }
        }
    }

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
