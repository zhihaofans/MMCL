package dev.mmcl.core.net

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 下载器引擎使用的传输层抽象。
 *
 * 只做"把给定 URL 的内容取回来"，**不含任何地址**：地址全部来自清单/版本详情数据
 * （也就是用户安装的来源插件）。测试里可以注入假实现，完全不碰网络。
 */
interface HttpTransport {
    /** 拉取文本内容（版本清单、版本详情等小文件）。 */
    suspend fun getText(url: String): String

    /**
     * 把 URL 内容写到 [target]（调用方负责给临时路径并在校验后移动）。
     *
     * @throws IOException 网络错误或非 2xx 状态码
     */
    suspend fun downloadTo(url: String, target: Path)
}

/**
 * 基于 JDK `HttpClient` 的默认实现，不引入额外 HTTP 依赖。
 *
 * @param userAgent 请求标识，建议 `MMCL/<版本>`
 */
class JdkHttpTransport(
    private val userAgent: String = "MMCL",
    private val timeout: Duration = Duration.ofSeconds(30),
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) : HttpTransport {
    override suspend fun getText(url: String): String =
        withContext(Dispatchers.IO) {
            val response = send(url, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            response.body()
        }

    override suspend fun downloadTo(url: String, target: Path) {
        withContext(Dispatchers.IO) {
            val response =
                send(
                    url,
                    HttpResponse.BodyHandlers.ofFile(
                        target,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.WRITE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    ),
                )
            if (response.statusCode() !in 200..299) {
                throw IOException("HTTP ${response.statusCode()}：$url")
            }
        }
    }

    private fun <T> send(url: String, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
        val request =
            HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .GET()
                .build()
        val response =
            try {
                client.send(request, handler)
            } catch (e: IOException) {
                throw IOException("请求失败：$url", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("请求被中断：$url", e)
            }
        if (response.statusCode() !in 200..299) {
            throw IOException("HTTP ${response.statusCode()}：$url")
        }
        return response
    }
}
