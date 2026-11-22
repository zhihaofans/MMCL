package dev.mmcl.plugin.source.bmclapi

import dev.mmcl.api.capability.UrlMirror
import dev.mmcl.api.capability.VersionManifestSource
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BMCLAPI 版本源（国内镜像）。
 *
 * 覆盖三类地址（BMCLAPI 文档的映射规则）：
 * - 版本清单 / 版本 JSON / client jar / 资源索引：替换 Mojang 的 `piston-meta`、`piston-data`、
 *   `launchermeta`、`launcher.mojang.com` 主机；
 * - 库文件：`libraries.minecraft.net` → `/maven`；
 * - 资源对象：`resources.download.minecraft.net` → `/assets`（同时由 [assetObjectUrl] 提供）。
 *
 * 地址只存在于插件里，应用本身不含任何下载地址（`AGENTS.md` §3）。
 *
 * @param baseUrl 镜像根地址，测试可替换
 * @param manifestUrl 清单地址
 * @param timeout 网络超时
 * @param localeProvider 当前界面语言，用于展示名与来源标注
 */
class BmclapiVersionSource(
    private val baseUrl: String = DEFAULT_BASE,
    private val manifestUrl: String = "$baseUrl/mc/game/version_manifest_v2.json",
    private val userAgent: String = "MMCL",
    private val timeout: Duration = Duration.ofSeconds(20),
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
    private val localeProvider: () -> String = { ENGLISH },
) : VersionManifestSource {
    override val id: String = "bmclapi"

    override val displayName: String
        get() = if (isChinese(localeProvider())) "BMCLAPI 国内镜像" else "BMCLAPI mirror"

    /** 镜像优先于官方源：装了它就默认先走国内线路。 */
    override val priority: Int = 10

    /** BMCLAPI 使用条款要求标明来源。 */
    override val attribution: String
        get() =
            if (isChinese(localeProvider())) {
                "资源来自 BMCLAPI（bangbang93）"
            } else {
                "Downloads served by BMCLAPI (bangbang93)"
            }

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
                    throw IOException("请求 BMCLAPI 版本清单失败：$manifestUrl", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("请求 BMCLAPI 版本清单被中断：$manifestUrl", e)
                }
            if (response.statusCode() !in 200..299) {
                throw IOException("HTTP ${response.statusCode()}：$manifestUrl")
            }
            response.body()
        }

    /** 资源对象：`/assets/<前两位>/<hash>`。 */
    override fun assetObjectUrl(hash: String): String? {
        if (hash.length != SHA1_LENGTH) return null
        return "$baseUrl/assets/${hash.substring(0, 2)}/$hash"
    }

    /**
     * 把 Mojang 官方地址映射到 BMCLAPI；不认识的地址返回 `null`（按原地址下载）。
     *
     * 列表顺序有意义：先匹配具体主机，再匹配库/资源基址。
     */
    override fun resolveUrl(original: String): String? {
        val url = original.trim()
        for ((prefix, replacement) in rewrites()) {
            if (url.startsWith(prefix)) {
                return replacement + url.removePrefix(prefix)
            }
        }
        return null
    }

    /** 当前镜像的改写表；[baseUrl] 参与拼接，便于测试用假地址。 */
    internal fun rewrites(): List<Pair<String, String>> =
        listOf(
            "https://piston-meta.mojang.com" to baseUrl,
            "https://piston-data.mojang.com" to baseUrl,
            "https://launchermeta.mojang.com" to baseUrl,
            "http://launchermeta.mojang.com" to baseUrl,
            "https://launcher.mojang.com" to baseUrl,
            "http://launcher.mojang.com" to baseUrl,
            "https://libraries.minecraft.net" to "$baseUrl/maven",
            "http://libraries.minecraft.net" to "$baseUrl/maven",
            "https://resources.download.minecraft.net" to "$baseUrl/assets",
            "http://resources.download.minecraft.net" to "$baseUrl/assets",
        )

    companion object {
        /** BMCLAPI 根地址（插件内部常量，应用不含它）。 */
        const val DEFAULT_BASE: String = "https://bmclapi2.bangbang93.com"

        private const val SHA1_LENGTH = 40
        private const val ENGLISH = "en"
    }
}

/**
 * BMCLAPI 的通用地址镜像：目前覆盖 Fabric Maven。
 *
 * 只做「官方地址 → 镜像地址」的确定性替换，不缓存、不联网；镜像不可用时由调用方回退。
 */
internal class BmclapiUrlMirror : UrlMirror {
    override val id: String = "bmclapi"

    override fun resolve(url: String): String? =
        when {
            url.startsWith(FABRIC_MAVEN) -> MIRROR_MAVEN + url.removePrefix(FABRIC_MAVEN)
            else -> null
        }

    private companion object {
        const val FABRIC_MAVEN = "https://maven.fabricmc.net/"
        const val MIRROR_MAVEN = "https://bmclapi2.bangbang93.com/maven/"
    }
}
