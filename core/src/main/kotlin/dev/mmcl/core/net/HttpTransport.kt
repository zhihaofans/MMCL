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

    /**
     * 带字节回调的下载，用于限速。
     *
     * 默认实现忽略回调、退回两个参数的版本；支持流式的实现（[JdkHttpTransport]）会覆盖它。
     *
     * @param onBytes 每读到一块数据回调一次，调用方在这里做限速
     */
    suspend fun downloadTo(url: String, target: Path, onBytes: suspend (Int) -> Unit) {
        downloadTo(url, target)
    }
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
        downloadTo(url, target) {}
    }

    override suspend fun downloadTo(url: String, target: Path, onBytes: suspend (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val response = send(url, HttpResponse.BodyHandlers.ofInputStream())
            java.nio.file.Files.newOutputStream(
                target,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
            ).use { output ->
                response.body().use { input ->
                    val buffer = ByteArray(CHUNK_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        onBytes(read)
                    }
                }
            }
        }
    }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
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
