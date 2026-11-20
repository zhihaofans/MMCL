package dev.mmcl.app.appearance

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import dev.mmcl.core.net.HttpTransport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.Logger

/**
 * 皮肤头像加载器：下载 → 内存 + 磁盘缓存 → 解码成 [ImageBitmap]。
 *
 * 地址来自认证插件（`AuthProvider.skinUrl`），启动器不内置任何皮肤地址。
 * 下载失败只记日志并返回 `null`，界面回退到首字母占位头像。
 *
 * @param transport HTTP 传输层（与下载器共用）
 * @param cacheDir 磁盘缓存目录，通常是 `<dataDir>/cache/skins`
 */
class SkinLoader(
    private val transport: HttpTransport,
    private val cacheDir: Path,
    private val logger: Logger,
) {
    private val memory = ConcurrentHashMap<String, ImageBitmap>()

    /** 同一 URL 的加载串行化：首页和顶栏会同时请求同一张皮肤，并发写同一个临时文件会互相踩。 */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** 读取皮肤；`url` 为空或加载失败时返回 `null`（不抛异常）。 */
    suspend fun load(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        memory[url]?.let { return it }

        val lock = locks.getOrPut(url) { Mutex() }
        return lock.withLock {
            memory[url]?.let { return@withLock it }

            val file = cacheDir.resolve(fileName(url))
            val bitmap =
                withContext(Dispatchers.IO) {
                    if (Files.isRegularFile(file)) decode(file) else download(url, file)?.let { decode(file) }
                } ?: return@withLock null

            memory[url] = bitmap
            bitmap
        }
    }

    /** 下载到缓存文件；成功返回 [Unit]，失败返回 `null`。 */
    private suspend fun download(url: String, target: Path): Unit? =
        withContext(Dispatchers.IO) {
            Files.createDirectories(cacheDir)
            val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
            runCatching {
                transport.downloadTo(url, temp)
                runCatching {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }.getOrElse {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
                Unit
            }.onFailure {
                logger.warn("皮肤下载失败（{}）：{}", url, it.message)
                runCatching { Files.deleteIfExists(temp) }
            }.getOrNull()
        }

    /** 解码缓存文件；损坏时删掉，避免下次继续读到坏文件。 */
    private fun decode(file: Path): ImageBitmap? =
        runCatching { Files.newInputStream(file).use { loadImageBitmap(it) } }
            .onFailure {
                logger.warn("皮肤文件损坏，已删除：{}（{}）", file, it.message)
                runCatching { Files.deleteIfExists(file) }
            }
            .getOrNull()

    private companion object {
        /** 用 URL 的 SHA-1 做文件名，避免把 URL 里的字符带进路径。 */
        fun fileName(url: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) } + ".png"
        }
    }
}
