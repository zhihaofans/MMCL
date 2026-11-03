package dev.mmcl.plugin.source.mojang

import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mojang 官方版本清单源。
 *
 * 单独做成插件（而不是内置在应用里）：应用本身不包含任何下载地址，
 * 是否从官方拉取版本清单完全由用户安装的插件决定。
 *
 * @param manifestUrl 清单地址，测试或私有镜像可替换
 * @param userAgent 请求标识
 * @param timeout 网络超时
 */
class MojangVersionSource(
    private val manifestUrl: String = DEFAULT_MANIFEST_URL,
    private val userAgent: String = "MMCL",
    private val timeout: Duration = Duration.ofSeconds(20),
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) : VersionManifestSource {
    override val id: String = "mojang"
    override val displayName: String = "Mojang 官方"
    override val priority: Int = 100

    override suspend fun fetchManifestJson(): String =
        withContext(Dispatchers.IO) {
            val request =
                HttpRequest.newBuilder(URI.create(manifestUrl))
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .GET()
                    .build()
            val response =
                try {
                    client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                } catch (e: IOException) {
                    throw IOException("请求版本清单失败：$manifestUrl", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("请求版本清单被中断：$manifestUrl", e)
                }
            if (response.statusCode() !in 200..299) {
                throw IOException("HTTP ${response.statusCode()}：$manifestUrl")
            }
            response.body()
        }

    /**
     * Mojang 资源 CDN：`objects/<前两位>/<hash>`。
     *
     * 这个地址**只存在于插件里**，应用本身不含任何下载地址。
     */
    override fun assetObjectUrl(hash: String): String? {
        if (hash.length != SHA1_LENGTH) return null
        return "$DEFAULT_ASSET_BASE/${hash.substring(0, 2)}/$hash"
    }

    companion object {
        /** Mojang 资源对象基址（插件内部常量）。 */
        const val DEFAULT_ASSET_BASE: String = "https://resources.download.minecraft.net"

        private const val SHA1_LENGTH = 40

        /** Mojang 官方版本清单地址。 */
        const val DEFAULT_MANIFEST_URL: String =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}

/** 注册 [MojangVersionSource] 的插件入口。 */
class MojangSourcePlugin : Plugin {
    override val id: String = "dev.mmcl.source.mojang.plugin"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT

    override fun onLoad(context: PluginContext) {
        context.services.register(VersionManifestSource::class, MojangVersionSource())
        context.logger.info("Mojang 官方版本源已注册")
    }
}
