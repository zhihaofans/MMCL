package dev.mmcl.plugin.runtime.foojay

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.ArchiveFormat
import dev.mmcl.api.capability.JavaRuntimeProvider
import dev.mmcl.api.capability.RuntimeDownload
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.plugin.PluginContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * foojay Disco API 运行时来源插件：一个插件提供多个 Java 发行版。
 *
 * 地址只存在于插件里：应用本身不含任何下载地址。每个发行版注册成一个独立的
 * [JavaRuntimeProvider]，用户在「设置 → Java 运行时来源」里挑一个即可。
 *
 * 与 `runtime-adoptium` 的分工：那个插件只提供 Temurin，这个插件提供其余发行版，
 * 所以这里的清单里**不含** Temurin，避免设置页出现两个一模一样的选项。
 */
class FoojayRuntimePlugin : Plugin {
    override val id: String = "dev.mmcl.runtime.foojay"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val title: String
        get() = if (isChinese(localeProvider())) "foojay 多发行版" else "foojay distributions"
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.RUNTIME

    /** `onLoad` 之前不读语言，先按英文兜底。 */
    private var localeProvider: () -> String = { ENGLISH }

    override fun onLoad(context: PluginContext) {
        localeProvider = { context.locale }
        for (spec in FoojayDistributions.ALL) {
            context.services.register(
                JavaRuntimeProvider::class,
                FoojayRuntimeProvider(
                    distribution = spec.distribution,
                    displayName = spec.displayName,
                    priority = spec.priority,
                ),
            )
        }
        context.logger.info("foojay 运行时来源已注册：{} 个发行版", FoojayDistributions.ALL.size)
    }
}

/** 一个发行版 [distribution] 就是 foojay 的 `distribution` 参数取值。 */
data class FoojayDistribution(
    val distribution: String,
    val displayName: String,
    val priority: Int,
)

/** 本插件提供的发行版清单。Temurin 由 `runtime-adoptium` 单独提供，这里不重复。 */
object FoojayDistributions {
    val ALL: List<FoojayDistribution> =
        listOf(
            FoojayDistribution("zulu", "Azul Zulu", 110),
            FoojayDistribution("liberica", "BellSoft Liberica", 120),
            FoojayDistribution("microsoft", "Microsoft OpenJDK", 130),
            FoojayDistribution("corretto", "Amazon Corretto", 140),
            FoojayDistribution("dragonwell", "Alibaba Dragonwell", 150),
            FoojayDistribution("oracle_open_jdk", "Oracle OpenJDK", 160),
        )
}

/**
 * 通过 foojay Disco API 解析某个发行版在目标平台上的下载地址。
 *
 * 只把「目标平台」作为查询参数发给 API：`operating_system` / `architecture` / `archive_type`，
 * 由 API 负责挑出对应平台的包，插件不自己拼压缩包名，避免拼错平台。
 *
 * @param distribution foojay 的 `distribution` 参数，例如 `zulu`
 * @param displayName 展示名
 * @param priority 排在最前面的来源优先被自动选中
 * @param baseUrl API 基址，测试可替换
 * @param fetchJson 取 JSON 的函数，测试可注入
 */
class FoojayRuntimeProvider(
    private val distribution: String,
    override val displayName: String,
    override val priority: Int = 100,
    private val baseUrl: String = DEFAULT_BASE,
    private val fetchJson: suspend (String) -> String = ::httpGet,
) : JavaRuntimeProvider {
    override val id: String = "foojay:$distribution"

    override suspend fun resolve(
        majorVersion: Int,
        os: OperatingSystem,
        arch: Architecture,
    ): RuntimeDownload? {
        val osToken =
            when (os) {
                OperatingSystem.MACOS -> "mac"
                OperatingSystem.WINDOWS -> "windows"
                OperatingSystem.LINUX -> "linux"
            }
        val archToken =
            when (arch) {
                Architecture.ARM64 -> "aarch64"
                Architecture.X86_64 -> "x64"
                Architecture.UNKNOWN -> return null
            }
        val format = if (os == OperatingSystem.WINDOWS) ArchiveFormat.ZIP else ArchiveFormat.TAR_GZ
        val archiveType = if (format == ArchiveFormat.ZIP) "zip" else "tar.gz"

        val url =
            buildString {
                append(baseUrl)
                append("/packages?version=").append(majorVersion)
                append("&operating_system=").append(osToken)
                append("&architecture=").append(archToken)
                append("&archive_type=").append(archiveType)
                append("&package_type=jdk&latest=available")
                append("&distribution=").append(distribution)
            }
        return parse(fetchJson(url), format)
    }

    /** 解析 Disco API 响应里的第一个包；没有可用包返回 `null`。 */
    internal fun parse(body: String, format: ArchiveFormat): RuntimeDownload? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val first = (root["result"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null

        val download =
            (first["links"] as? JsonObject)?.get("pkg_download_redirect")?.jsonPrimitive?.contentOrNull
                ?: first["direct_download_uri"]?.jsonPrimitive?.contentOrNull
        if (download.isNullOrBlank()) return null

        // checksum 只有在类型明确是 sha1 时才能喂给安装器（它只认 SHA-1）
        val checksumType = first["checksum_type"]?.jsonPrimitive?.contentOrNull
        val sha1 = first["checksum"]?.jsonPrimitive?.contentOrNull?.takeIf { checksumType == "sha1" }

        return RuntimeDownload(
            url = download,
            sha1 = sha1,
            format = format,
            fileName = first["filename"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private companion object {
        /** foojay Disco API 基址（插件内部常量）。 */
        const val DEFAULT_BASE: String = "https://api.foojay.io/disco/v3.0"

        val json = Json { ignoreUnknownKeys = true }
    }
}

/** 用 JDK 的 `HttpClient` 取 JSON；不引入额外 HTTP 依赖。 */
private suspend fun httpGet(url: String): String =
    withContext(Dispatchers.IO) {
        val client =
            HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        val request =
            HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()
        val response =
            try {
                client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            } catch (e: IOException) {
                throw IOException("请求运行时来源失败：$url", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("请求运行时来源被中断：$url", e)
            }
        if (response.statusCode() !in 200..299) {
            throw IOException("HTTP ${response.statusCode()}：$url")
        }
        response.body()
    }

private const val USER_AGENT = "MMCL"

/** 缺省语言：`onLoad` 之前用英文兜底。 */
private const val ENGLISH = "en"

/** 界面语言是否为中文；插件展示名跟随 `PluginContext.locale`，不写死。 */
private fun isChinese(locale: String): Boolean = locale.lowercase().startsWith("zh")

private val TIMEOUT: Duration = Duration.ofSeconds(20)
