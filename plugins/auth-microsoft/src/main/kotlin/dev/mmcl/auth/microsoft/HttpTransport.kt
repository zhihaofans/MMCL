package dev.mmcl.auth.microsoft

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HTTP 抽口：插件里所有网络请求都走这里。
 *
 * 测试注入假实现即可，不碰真实网络（见 `ARCHITECTURE.md` 的测试约定）。
 */
internal interface HttpTransport {
    /** `application/x-www-form-urlencoded` POST，返回响应体。 */
    suspend fun postForm(url: String, form: Map<String, String>): String

    /** `application/json` POST；[headers] 用于附加 `Authorization` 之类。 */
    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String

    /** 带 Bearer 的 GET，返回响应体。 */
    suspend fun getJson(url: String, bearerToken: String): String
}

/** 非 2xx 响应；[status] 为 0 表示连接层失败。 */
internal class HttpStatusException(val status: Int, val body: String) :
    IOException("HTTP $status：${body.take(300)}")

/** 基于 JDK `HttpClient` 的实现，跟随重定向、15 秒连接超时。 */
internal class JdkHttpTransport(
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) : HttpTransport {
    override suspend fun postForm(url: String, form: Map<String, String>): String {
        val body = form.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
        )
    }

    override suspend fun postJson(url: String, json: String, headers: Map<String, String>): String {
        val builder =
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        headers.forEach { (key, value) -> builder.header(key, value) }
        return send(builder)
    }

    override suspend fun getJson(url: String, bearerToken: String): String =
        send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $bearerToken")
                .GET(),
        )

    private suspend fun send(builder: HttpRequest.Builder): String =
        withContext(Dispatchers.IO) {
            val request = builder.timeout(Duration.ofSeconds(30)).build()
            val response =
                try {
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                } catch (e: IOException) {
                    throw HttpStatusException(0, e.message.orEmpty())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw HttpStatusException(0, "请求被中断")
                }
            if (response.statusCode() !in 200..299) {
                throw HttpStatusException(response.statusCode(), response.body())
            }
            response.body()
        }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val USER_AGENT = "MMCL"
    }
}
