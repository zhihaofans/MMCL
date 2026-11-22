package dev.mmcl.core.version

import dev.mmcl.core.net.HttpTransport
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger

/** 版本详情获取/解析失败。 */
class VersionDetailException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 版本详情服务：按清单条目里的 URL 拉取 `<version>.json`，落到
 * `<versionsDir>/<id>/<id>.json` 并复用本地副本。
 *
 * 地址来自 [VersionManifestEntry.url]（源头是插件），这里不写死任何下载地址。
 *
 * @param versionsDir 通常为 `<dataDir>/versions`
 * @param transport 传输层，测试可注入
 * @param logger 运行日志
 */
class VersionDetailService(
    private val versionsDir: Path,
    private val transport: HttpTransport,
    private val logger: Logger,
    /** 版本详情地址改写钩子，由来源插件的 `resolveUrl` 提供；返回 `null` 表示不改。 */
    private val resolveUrl: (String) -> String? = { null },
) {
    private val mutex = Mutex()
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /** 某个版本的本地详情文件路径。 */
    fun detailFile(versionId: String): Path = versionsDir.resolve(versionId).resolve("$versionId.json")

    /**
     * 获取版本详情。
     *
     * @param entry 版本清单里的条目
     * @param forceRefresh 忽略本地文件，强制重新拉取
     */
    suspend fun load(entry: VersionManifestEntry, forceRefresh: Boolean = false): VersionDetail =
        mutex.withLock {
            resolveInheritance(loadRaw(entry, forceRefresh))
        }

    /** 加载原始详情（不解析继承）。 */
    private suspend fun loadRaw(entry: VersionManifestEntry, forceRefresh: Boolean): VersionDetail {
            val file = detailFile(entry.id)
            if (!forceRefresh && Files.isRegularFile(file)) {
                parse(readFile(file), entry.id)?.let {
                    logger.debug("命中版本详情缓存：{}", file)
                    return it
                }
                logger.warn("本地版本详情不可用，重新拉取：{}", file)
            }
            if (entry.url.isBlank()) {
                throw VersionDetailException("版本 ${entry.id} 的清单条目没有详情地址")
            }
            val body =
                try {
                    transport.getText(resolveUrl(entry.url) ?: entry.url)
                } catch (e: IOException) {
                    throw VersionDetailException("拉取版本详情失败：${entry.id}", e)
                }
            val detail = parse(body, entry.id) ?: throw VersionDetailException("版本 ${entry.id} 的详情 JSON 无法解析")
            writeAtomically(file, body)
            logger.info("版本详情已更新：{}（mainClass={}）", detail.id, detail.mainClass)
            return detail
    }

    /**
     * 解析 `inheritsFrom`：加载器版本只有自己的 mainClass/libraries/arguments，
     * client jar、assets、logging、Java 版本都来自父版本（例如 26.3）。
     *
     * 父版本必须已安装到本地（加载器安装前置），缺失时给出可操作的错误。
     */
    private fun resolveInheritance(detail: VersionDetail, depth: Int = 0): VersionDetail {
        val parentId = detail.inheritsFrom?.takeIf { it.isNotBlank() } ?: return detail
        if (depth >= MAX_INHERITANCE_DEPTH) {
            throw VersionDetailException("版本 ${detail.id} 的继承链超过 $MAX_INHERITANCE_DEPTH 层")
        }
        val file = detailFile(parentId)
        if (!Files.isRegularFile(file)) {
            throw VersionDetailException("缺少父版本 $parentId 的版本详情，请先安装基础版本")
        }
        val parent =
            parse(readFile(file), parentId)
                ?: throw VersionDetailException("父版本 $parentId 的详情 JSON 无法解析")
        return merge(resolveInheritance(parent, depth + 1), detail)
    }

    /** 把父版本与子版本合并成一份可直接启动/规划的详情。 */
    private fun merge(parent: VersionDetail, child: VersionDetail): VersionDetail {
        val root = parent.rootId ?: parent.id
        return child.copy(
            mainClass = child.mainClass.ifBlank { parent.mainClass },
            inheritsFrom = root,
            rootId = root,
            assets = child.assets.ifBlank { parent.assets },
            assetIndex = child.assetIndex ?: parent.assetIndex,
            // 子版本没有 client jar，必须继承父版本的 downloads
            downloads = parent.downloads + child.downloads,
            libraries = parent.libraries + child.libraries,
            logging = child.logging ?: parent.logging,
            javaVersion = child.javaVersion ?: parent.javaVersion,
            arguments =
                VersionArguments(
                    game = parent.arguments.game + child.arguments.game,
                    jvm = parent.arguments.jvm + child.arguments.jvm,
                ),
        )
    }

    /** 该版本的详情是否已缓存在本地。 */
    fun isCached(versionId: String): Boolean = Files.isRegularFile(detailFile(versionId))

    private fun parse(body: String, expectedId: String): VersionDetail? =
        try {
            val detail = json.decodeFromString(VersionDetail.serializer(), body)
            if (detail.id != expectedId) {
                logger.warn("版本详情 id 与清单不一致：{} != {}", detail.id, expectedId)
            }
            detail
        } catch (e: SerializationException) {
            logger.warn("版本详情 JSON 解析失败：{}", e.message)
            null
        }

    private fun readFile(file: Path): String =
        try {
            Files.readString(file, Charsets.UTF_8)
        } catch (e: IOException) {
            logger.warn("读取版本详情失败：{}", e.message)
            ""
        }

    private companion object {
        /** 继承链上限：防止损坏的 JSON 造成死循环。 */
        const val MAX_INHERITANCE_DEPTH = 5
    }

    private fun writeAtomically(file: Path, body: String) {
        val parent = file.toAbsolutePath().parent ?: return
        try {
            Files.createDirectories(parent)
            val temp = Files.createTempFile(parent, "version-detail", ".tmp")
            Files.writeString(temp, body, Charsets.UTF_8)
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            logger.warn("写入版本详情失败：{}", e.message)
        }
    }
}
