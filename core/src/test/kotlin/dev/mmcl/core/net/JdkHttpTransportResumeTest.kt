package dev.mmcl.core.net

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class JdkHttpTransportResumeTest {
    private val content = ("resume-test-" + "y".repeat(2000)).toByteArray()
    private lateinit var server: HttpServer
    private lateinit var base: String
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("mmcl-transport-resume")
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/range.jar") { exchange ->
            val start =
                exchange.requestHeaders.getFirst("Range")
                    ?.removePrefix("bytes=")
                    ?.substringBefore('-')
                    ?.toIntOrNull() ?: 0
            val slice = content.copyOfRange(start.coerceIn(0, content.size), content.size)
            exchange.sendResponseHeaders(if (start > 0) 206 else 200, slice.size.toLong())
            exchange.responseBody.use { it.write(slice) }
        }
        server.createContext("/plain.jar") { exchange ->
            // 故意忽略 Range：模拟不支持续传的服务器
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.use { it.write(content) }
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `服务器支持 Range 时从断点续传`() = runTest {
        val target = dir.resolve("part.jar")
        Files.write(target, content.copyOf(800))

        val resumed = JdkHttpTransport(userAgent = "test").resumeTo("$base/range.jar", target) {}

        assertTrue(resumed)
        assertTrue(content.contentEquals(Files.readAllBytes(target)))
    }

    @Test
    fun `服务器忽略 Range 时整份重写并返回 false`() = runTest {
        val target = dir.resolve("part2.jar")
        Files.write(target, content.copyOf(800))

        val resumed = JdkHttpTransport(userAgent = "test").resumeTo("$base/plain.jar", target) {}

        assertFalse(resumed)
        assertTrue(content.contentEquals(Files.readAllBytes(target)))
    }

    @Test
    fun `没有部分文件时直接完整下载`() = runTest {
        val target = dir.resolve("fresh.jar")

        val resumed = JdkHttpTransport(userAgent = "test").resumeTo("$base/range.jar", target) {}

        assertFalse(resumed)
        assertEquals(content.size.toLong(), Files.size(target))
    }
}
