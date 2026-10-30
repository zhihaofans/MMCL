package dev.mmcl.core.version

import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.api.service.SettingsService
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger

/**
 * 版本清单服务：选源、拉取、解析、缓存。
 *
 * **不内置任何源**：源由插件注册（[VersionManifestSource]），core 只负责"顺序尝试 + 校验 + 缓存"。
 * 这样应用本身不包含任何下载地址，能否获取游戏版本取决于用户装了什么插件。
 *
 * 选源顺序：
 * 1. 设置项 `version.source` 指定的源（值 `auto` 或不设置表示自动）；
 * 2. 其余源按 `priority` 升序、id 升序。
 *
 * 回退顺序：
 * 1. 逐个源尝试，解析失败或抛异常都跳过；
 * 2. 全部失败 → 本地缓存（可能过期，日志告警）；
 * 3. 连缓存都没有 → 抛 [NoVersionSourceException]（无源）或 [VersionManifestException]（有源但全挂）。
 *
 * 缓存自包含（`{fetchedAt, sourceId, manifest}`），写入走临时文件 + 原子替换；
 * 解析成功才落盘，脏数据不会污染缓存。
 *
 * @param cacheFile 缓存路径，通常 `<dataDir>/cache/version_manifest_v2.json`
 * @param logger 运行日志
 * @param sources 可用源提供者；每次加载时求值，插件可能随时注册
 * @param settings 读取用户指定的源；为 `null` 时按优先级自动选
 * @param clock 当前时间（毫秒），测试可注入
 * @param cacheTtlMillis 缓存有效期
 */
class VersionManifestService(
    private val cacheFile: Path,
    private val logger: Logger,
    private val sources: () -> List<VersionManifestSource> = { emptyList() },
    private val settings: SettingsService? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
) {
    private val mutex = Mutex()
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * 获取版本清单。
     *
     * @param forceRefresh 忽略缓存有效期，强制逐个源尝试
     * @throws NoVersionSourceException 没有源插件且没有缓存
     * @throws VersionManifestException 有源但全部失败且没有缓存
     */
    suspend fun load(forceRefresh: Boolean = false): VersionManifest =
        mutex.withLock {
            val ordered = orderedSources()
            val cached = readCacheEntry()

            if (ordered.isEmpty()) {
                if (cached != null) {
                    logger.warn("未安装版本源插件，使用本地缓存（源 {}，可能过期）", cached.sourceId)
                    return@withLock cached.manifest
                }
                logger.warn("未安装版本源插件，且没有本地缓存")
                throw NoVersionSourceException()
            }

            if (!forceRefresh && cached != null &&
                cached.sourceId in ordered.map { it.id } &&
                clock() - cached.fetchedAt <= cacheTtlMillis
            ) {
                logger.debug("命中版本清单缓存：{}（源 {}）", cacheFile, cached.sourceId)
                return@withLock cached.manifest
            }

            var lastError: Throwable? = null
            for (source in ordered) {
                try {
                    val manifest = parse(source.fetchManifestJson(), source.id)
                    writeCache(manifest, source.id)
                    logger.info(
                        "版本清单已更新：源 {}，最新正式版 {}，共 {} 个版本",
                        source.id,
                        manifest.latest.release,
                        manifest.versions.size,
                    )
                    return@withLock manifest
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    lastError = t
                    logger.warn("版本源 {} 获取失败：{}", source.id, t.message)
                }
            }

            if (cached != null) {
                logger.warn("所有版本源均失败，回退到本地缓存（源 {}，可能过期）", cached.sourceId)
                return@withLock cached.manifest
            }
            logger.error("所有版本源均失败，且没有本地缓存", lastError)
            throw lastError as? VersionManifestException
                ?: VersionManifestException("所有版本源均不可用", lastError)
        }

    /**
     * 当前可用的源，按实际尝试顺序返回：用户指定的排最前，其余按优先级。
     */
    fun orderedSources(): List<VersionManifestSource> {
        val all = sources().sortedWith(compareBy({ it.priority }, { it.id }))
        val preferred =
            settings?.getString(SOURCE_KEY)
                ?.takeIf { it.isNotBlank() && it != SOURCE_AUTO }
                ?: return all
        val picked = all.firstOrNull { it.id == preferred } ?: return all
        return listOf(picked) + all.filterNot { it.id == picked.id }
    }

    /**
     * 用户指定版本源，写入设置，下次 [load] 生效。
     */
    fun selectSource(sourceId: String) {
        settings?.putString(SOURCE_KEY, sourceId)
    }

    /** 是否至少装了一个版本源插件；界面用它决定显示"请安装插件"还是"刷新"。 */
    fun hasSource(): Boolean = sources().isNotEmpty()

    /** 缓存的写入时间（epoch 毫秒）；没有缓存返回 `null`。 */
    fun cachedAt(): Long? = readCacheEntry()?.fetchedAt

    /** 缓存来自哪个源；没有缓存返回 `null`。 */
    fun cachedSourceId(): String? = readCacheEntry()?.sourceId

    private fun parse(body: String, sourceId: String): VersionManifest =
        try {
            json.decodeFromString(VersionManifest.serializer(), body)
        } catch (e: SerializationException) {
            throw VersionManifestException("版本源 $sourceId 返回的 JSON 无法解析", e)
        }

    private fun readCacheEntry(): CachedManifest? {
        if (!Files.isRegularFile(cacheFile)) return null
        return try {
            json.decodeFromString(CachedManifest.serializer(), Files.readString(cacheFile, Charsets.UTF_8))
        } catch (t: Exception) {
            logger.warn("本地版本清单缓存不可用，已忽略：{}", t.message)
            null
        }
    }

    private fun writeCache(manifest: VersionManifest, sourceId: String) {
        val parent = cacheFile.toAbsolutePath().parent ?: return
        try {
            Files.createDirectories(parent)
            val payload =
                json.encodeToString(
                    CachedManifest.serializer(),
                    CachedManifest(fetchedAt = clock(), sourceId = sourceId, manifest = manifest),
                )
            val temp = Files.createTempFile(parent, "version-manifest", ".tmp")
            Files.writeString(temp, payload, Charsets.UTF_8)
            Files.move(temp, cacheFile, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: IOException) {
            // 缓存写失败不影响本次使用
            logger.warn("写入版本清单缓存失败：{}", t.message)
        }
    }

    companion object {
        /** 设置键：指定版本源 id，`auto` 表示按优先级自动选。 */
        const val SOURCE_KEY: String = "version.source"

        /** [SOURCE_KEY] 的自动取值。 */
        const val SOURCE_AUTO: String = "auto"

        /** 默认缓存有效期：30 分钟。 */
        const val DEFAULT_CACHE_TTL_MILLIS: Long = 30 * 60 * 1000
    }
}

/** 自包含缓存结构：清单、来源、写入时间放同一个文件，避免三处不同步。 */
@Serializable
internal data class CachedManifest(
    val fetchedAt: Long,
    val sourceId: String = "",
    val manifest: VersionManifest,
)
