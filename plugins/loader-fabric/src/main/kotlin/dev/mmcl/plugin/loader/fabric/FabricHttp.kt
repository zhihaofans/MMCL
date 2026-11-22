package dev.mmcl.plugin.loader.fabric

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fabric meta 的取文本接口；测试可注入假实现。 */
internal fun interface FabricHttp {
    suspend fun getText(url: String): String
}

/** 基于 JDK HttpClient 的实现；不引第三方 HTTP 库。 */
internal class JdkFabricHttp(
    private val userAgent: String = "MMCL",
    private val timeout: Duration = Duration.ofSeconds(20),
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) : FabricHttp {
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
                    throw IOException("请求失败：$url", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("请求被中断：$url", e)
                }
            if (response.statusCode() !in 200..299) {
                throw IOException("HTTP ${response.statusCode()}：$url")
            }
            response.body()
        }
}
