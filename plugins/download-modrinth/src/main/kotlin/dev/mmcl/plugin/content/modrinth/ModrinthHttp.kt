package dev.mmcl.plugin.content.modrinth

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Modrinth 取文本接口；测试可注入假实现。 */
internal fun interface ModrinthHttp {
    suspend fun getText(url: String): String
}

/** 带 HTTP 状态码的请求失败。 */
internal class ModrinthHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

internal class JdkModrinthHttp(
    private val userAgent: String,
    private val timeout: Duration = Duration.ofSeconds(20),
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) : ModrinthHttp {
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
                    throw IOException("请求 Modrinth 失败：$url", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("请求 Modrinth 被中断：$url", e)
                }
            if (response.statusCode() !in 200..299) {
                throw ModrinthHttpException(response.statusCode(), "HTTP ${response.statusCode()}：$url")
            }
            response.body()
        }
}
