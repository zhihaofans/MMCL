package dev.mmcl.plugin.content.modrinth

import dev.mmcl.api.capability.ContentKind
import dev.mmcl.api.capability.ContentProject
import dev.mmcl.api.capability.ContentSearchQuery
import dev.mmcl.api.capability.ContentVersion
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger

/**
 * Modrinth API v2 客户端。
 *
 * 只读：搜索 / 列版本 / 取单个版本；文件下载由启动器按返回的 URL 自己做。
 * API 不需要 token，但要求带可识别的 User-Agent。
 */
internal class ModrinthClient(
    private val http: ModrinthHttp,
    private val logger: Logger,
    private val baseUrl: String = "https://api.modrinth.com",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: ContentSearchQuery): List<ContentProject> {
        val url = "$baseUrl/v2/search?${searchParams(query)}"
        val body = http.getText(url)
        return json.decodeFromString<SearchResponse>(body).hits.map { it.toContentProject() }
    }

    suspend fun versions(projectId: String, gameVersion: String, loader: String?): List<ContentVersion> {
        val params =
            buildList {
                add("game_versions=" + encode("""["$gameVersion"]"""))
                loader?.let { add("loaders=" + encode("""["$it"]""")) }
            }
        val url = "$baseUrl/v2/project/${encode(projectId)}/version?${params.joinToString("&")}"
        val body = http.getText(url)
        return json.decodeFromString<List<VersionDto>>(body).map { it.toContentVersion() }
    }

    suspend fun version(versionId: String): ContentVersion? {
        val url = "$baseUrl/v2/version/${encode(versionId)}"
        return try {
            json.decodeFromString<VersionDto>(http.getText(url)).toContentVersion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ModrinthHttpException) {
            if (e.statusCode == 404) return null
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("解析 Modrinth 版本失败：${e.message}", e)
        }
    }

    private fun searchParams(query: ContentSearchQuery): String {
        val facets =
            buildList {
                add(listOf("project_type:" + query.kind.modrinthType()))
                query.loader?.takeIf { it.isNotBlank() }?.let { add(listOf("categories:$it")) }
                add(listOf("versions:${query.gameVersion}"))
            }
        val facetsJson = facets.joinToString(",", "[", "]") { group -> group.joinToString(",", "[", "]") { "\"$it\"" } }
        return buildList {
            add("query=" + encode(query.query))
            add("facets=" + encode(facetsJson))
            add("limit=" + query.limit.coerceIn(1, 100))
        }.joinToString("&")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private fun ContentKind.modrinthType(): String =
        when (this) {
            ContentKind.MOD -> "mod"
            ContentKind.RESOURCE_PACK -> "resourcepack"
            ContentKind.SHADER_PACK -> "shader"
            ContentKind.MODPACK -> "modpack"
        }
}

/** 把搜索/版本结果暴露成 [dev.mmcl.api.capability.ContentSource]。 */
internal class ModrinthContentSource(
    private val client: ModrinthClient,
) : dev.mmcl.api.capability.ContentSource {
    override val id: String = "modrinth"
    override val displayName: String = "Modrinth"

    override suspend fun search(query: ContentSearchQuery): List<ContentProject> = client.search(query)

    override suspend fun versions(projectId: String, gameVersion: String, loader: String?): List<ContentVersion> =
        client.versions(projectId, gameVersion, loader)

    override suspend fun version(versionId: String): ContentVersion? = client.version(versionId)
}
