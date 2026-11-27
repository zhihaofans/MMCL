package dev.mmcl.plugin.update.checker

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 更新检查用的 HTTP 接口；测试可注入假实现。 */
internal interface UpdateHttp {
    /** 跟随 GitHub `releases/latest` 的 302，返回 tag（例如 `0.0.8`）。 */
    suspend fun latestTag(releasesLatestUrl: String): String?

    /** 取文本（更新索引）。 */
    suspend fun getText(url: String): String

    /** 下载到目标文件。 */
    suspend fun download(url: String, target: Path)
}

internal class JdkUpdateHttp(
    private val userAgent: String,
    private val timeout: Duration = Duration.ofSeconds(20),
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) : UpdateHttp {
    override suspend fun latestTag(releasesLatestUrl: String): String? =
        withContext(Dispatchers.IO) {
            val request =
                HttpRequest.newBuilder(URI.create(releasesLatestUrl))
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build()
            val response =
                try {
                    client.send(request, HttpResponse.BodyHandlers.discarding())
                } catch (e: IOException) {
                    throw IOException("请求 $releasesLatestUrl 失败", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("请求被中断：$releasesLatestUrl", e)
                }
            if (response.statusCode() !in 200..299) throw IOException("HTTP ${response.statusCode()}")
            val path = response.uri().path
            TAG_PATTERN.find(path)?.groupValues?.get(1)
        }

    override suspend fun getText(url: String): String =
        withContext(Dispatchers.IO) {
            val request =
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .GET()
                    .build()
            val response =
                try {
                    client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                } catch (e: IOException) {
                    throw IOException("请求 $url 失败", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("请求被中断：$url", e)
                }
            if (response.statusCode() !in 200..299) throw IOException("HTTP ${response.statusCode()}：$url")
            response.body()
        }

    override suspend fun download(url: String, target: Path) =
        withContext(Dispatchers.IO) {
            Files.createDirectories(target.parent)
            val request =
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .GET()
                    .build()
            val response =
                try {
                    client.send(
                        request,
                        HttpResponse.BodyHandlers.ofFile(
                            target,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE,
                        ),
                    )
                } catch (e: IOException) {
                    throw IOException("下载 $url 失败", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("下载被中断：$url", e)
                }
            if (response.statusCode() !in 200..299) {
                Files.deleteIfExists(target)
                throw IOException("HTTP ${response.statusCode()}：$url")
            }
        }

    private companion object {
        val TAG_PATTERN = Regex("""/releases/tag/([^/]+)$""")
    }
}
