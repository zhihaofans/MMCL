package dev.mmcl.plugin.loader.fabric

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger

/** 数据来自官方还是镜像。 */
internal enum class FabricSource {
    OFFICIAL,
    MIRROR,
}

/** 一次 meta 请求的结果。 */
internal data class FabricResponse(
    val body: String,
    val source: FabricSource,
)

/**
 * Fabric meta 客户端：官方优先，失败才回退 BMCLAPI 镜像。
 *
 * 镜像的 profile 实测可能缺字段（例如 26.3/0.20.1 少两个 JVM 参数），
 * 所以只作为兜底，并在返回结果里标明来源，让插件加警告。
 */
internal class FabricMetaClient(
    private val http: FabricHttp,
    private val logger: Logger,
    private val officialBase: String = OFFICIAL_META,
    private val mirrorBase: String = MIRROR_META,
) {
    suspend fun loaders(gameVersionId: String): FabricResponse =
        fetch(
            official = "$officialBase/v2/versions/loader/$gameVersionId",
            mirror = "$mirrorBase/v2/versions/loader/$gameVersionId",
            label = "loader 列表",
        )

    suspend fun profile(gameVersionId: String, loaderVersion: String): FabricResponse =
        fetch(
            official = "$officialBase/v2/versions/loader/$gameVersionId/$loaderVersion/profile/json",
            mirror = "$mirrorBase/v2/versions/loader/$gameVersionId/$loaderVersion/profile/json",
            label = "profile",
        )

    private suspend fun fetch(official: String, mirror: String, label: String): FabricResponse {
        try {
            return FabricResponse(http.getText(official), FabricSource.OFFICIAL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Fabric {} 官方源失败，改用 BMCLAPI 镜像：{}", label, e.message)
        }
        return try {
            FabricResponse(http.getText(mirror), FabricSource.MIRROR)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Fabric $label 官方与镜像都失败：${e.message}", e)
        }
    }

    internal companion object {
        const val OFFICIAL_META: String = "https://meta.fabricmc.net"
        const val MIRROR_META: String = "https://bmclapi2.bangbang93.com/fabric-meta"
    }
}

/** Fabric meta 的 JSON 解析与 profile 校验。 */
internal object FabricPayloads {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

    fun parseLoaders(body: String): List<dev.mmcl.api.capability.LoaderVersion> =
        json.decodeFromString<List<FabricLoaderEntry>>(body)
            .mapNotNull { entry ->
                val version = entry.loader.version.trim()
                if (version.isEmpty()) {
                    null
                } else {
                    dev.mmcl.api.capability.LoaderVersion(
                        version = version,
                        stable = entry.loader.stable,
                        note = if (entry.loader.stable) null else "beta",
                    )
                }
            }

    /**
     * 校验 profile 并取出版本 id。
     *
     * @throws IOException 缺字段、`inheritsFrom` 不匹配目标版本等致命问题
     */
    fun checkProfile(body: String, gameVersionId: String): ProfileCheck {
        val root =
            runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw IOException("Fabric profile 不是 JSON 对象") }
        val id =
            root.primitive("id")?.takeIf { it.isNotBlank() }
                ?: throw IOException("Fabric profile 缺少 id")
        val inherits = root.primitive("inheritsFrom")
        if (inherits != gameVersionId) {
            throw IOException("Fabric profile 的 inheritsFrom=$inherits，不是目标版本 $gameVersionId")
        }
        if (root.primitive("mainClass").isNullOrBlank()) {
            throw IOException("Fabric profile 缺少 mainClass")
        }
        val libraries = root["libraries"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
        if (libraries.isEmpty()) {
            throw IOException("Fabric profile 没有 libraries")
        }
        val warnings = mutableListOf<String>()
        val jvmArgs = root.arguments()?.get("jvm")?.let { runCatching { it.jsonArray }.getOrNull() }
        if (jvmArgs.isNullOrEmpty()) {
            warnings += "profile 没有 JVM 参数，启动可能失败"
        }
        return ProfileCheck(id = id, warnings = warnings)
    }

    /**
     * 把 profile 的库整理成 core 能直接用的形状：
     * - `url` → `downloads.artifact.url`（仓库根 + Maven 路径）
     * - `sha1`/`size` → `downloads.artifact`，缺失时尝试官方 `.sha1` sidecar
     * - 通过 [UrlMirror] 改写仓库地址（镜像不代理 `.sha1`，所以哈希始终从原地址取）
     */
    suspend fun normalizeLibraries(
        body: String,
        mirrors: List<dev.mmcl.api.capability.UrlMirror>,
        http: FabricHttp,
        logger: Logger,
    ): NormalizedProfile {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return NormalizedProfile(body, emptyList())
        val libraries = root["libraries"]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?: return NormalizedProfile(body, emptyList())
        val warnings = mutableListOf<String>()
        val rewritten =
            libraries.map { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@map element
                val name = obj.primitive("name") ?: return@map element
                val repository = obj.primitive("url") ?: return@map element
                val path = mavenPathOf(name)
                if (path == null) {
                    warnings += "无法解析 Maven 坐标：$name"
                    return@map element
                }
                val mirrored =
                    mirrors.firstNotNullOfOrNull { mirror ->
                        runCatching { mirror.resolve(repository) }.getOrNull()
                    } ?: repository
                val sha1 =
                    obj.primitive("sha1")?.takeIf { it.isNotBlank() }
                        ?: fetchSha1(repository + path, http, logger)?.also {
                            logger.debug("已从 {} 取到 sha1", name)
                        }
                if (sha1.isNullOrBlank()) {
                    warnings += "$name 没有哈希，下载后不校验"
                }
                val artifact =
                    buildJsonObject {
                        put("url", JsonPrimitive(mirrored + path))
                        put("path", JsonPrimitive(path))
                        put("sha1", JsonPrimitive(sha1.orEmpty()))
                        put("size", JsonPrimitive(obj.primitive("size")?.toLongOrNull() ?: 0L))
                    }
                JsonObject(
                    obj +
                        ("downloads" to buildJsonObject { put("artifact", artifact) }) +
                        ("url" to JsonPrimitive(mirrored)),
                )
            }
        val normalized = JsonObject(root + ("libraries" to JsonArray(rewritten)))
        return NormalizedProfile(normalized.toString(), warnings)
    }

    private suspend fun fetchSha1(url: String, http: FabricHttp, logger: Logger): String? =
        try {
            http.getText("$url.sha1").trim().substringBefore(' ').takeIf { it.isNotBlank() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug("取 sha1 失败（{}）：{}", url, e.message)
            null
        }

    private fun mavenPathOf(coordinates: String): String? {
        val parts = coordinates.trim().split(':')
        if (parts.size < 3) return null
        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val version = parts[2]
        if (group.isBlank() || artifact.isBlank() || version.isBlank()) return null
        val classifier = parts.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { "-$it" }.orEmpty()
        return "$group/$artifact/$version/$artifact-$version$classifier.jar"
    }

    private fun JsonObject.primitive(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    private fun JsonObject.arguments(): JsonObject? =
        this["arguments"]?.let { runCatching { it.jsonObject }.getOrNull() }
}

/** profile 校验结果。 */
internal data class ProfileCheck(
    val id: String,
    val warnings: List<String>,
)

/** 库归一化后的 profile JSON。 */
internal data class NormalizedProfile(
    val detailJson: String,
    val warnings: List<String>,
)

@kotlinx.serialization.Serializable
internal data class FabricLoaderEntry(
    val loader: FabricLoaderInfo = FabricLoaderInfo(),
)

@kotlinx.serialization.Serializable
internal data class FabricLoaderInfo(
    val version: String = "",
    val stable: Boolean = true,
)
