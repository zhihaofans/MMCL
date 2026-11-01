package dev.mmcl.core.download

import com.sun.net.httpserver.HttpServer
import dev.mmcl.core.net.JdkHttpTransport
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class FileDownloaderTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var root: Path
    private lateinit var server: HttpServer
    private lateinit var base: String

    private val okBytes = "hello-mmcl-downloader".toByteArray()
    private val okSha = sha1Hex(okBytes)
    private val flakyRequests = AtomicInteger()
    private val missingRequests = AtomicInteger()

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("mmcl-downloader-test")
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok.jar") { exchange ->
            exchange.sendResponseHeaders(200, okBytes.size.toLong())
            exchange.responseBody.use { it.write(okBytes) }
        }
        server.createContext("/bad.jar") { exchange ->
            val body = "corrupted-content".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/flaky.jar") { exchange ->
            if (flakyRequests.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            } else {
                exchange.sendResponseHeaders(200, okBytes.size.toLong())
                exchange.responseBody.use { it.write(okBytes) }
            }
        }
        server.createContext("/missing.jar") { exchange ->
            missingRequests.incrementAndGet()
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        root.toFile().deleteRecursively()
    }

    private fun downloader(
        concurrency: Int = 2,
        maxAttempts: Int = 3,
    ) = FileDownloader(
        JdkHttpTransport(userAgent = "test"),
        logger,
        concurrency,
        maxAttempts = maxAttempts,
        retryDelayMillis = { 0 },
    )

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `下载成功后写入目标路径`() = runTest {
        val file = PlannedFile("libraries/a/b.jar", "$base/ok.jar", okSha, okBytes.size.toLong())

        val report = downloader().download(root, listOf(file))

        assertTrue(report.isSuccess)
        assertEquals(1, report.downloaded)
        assertEquals(0, report.skipped)
        assertEquals(String(okBytes), Files.readString(root.resolve("libraries/a/b.jar")))
        assertFalse(Files.exists(root.resolve("libraries/a/b.jar.mmcl-tmp")))
    }

    @Test
    fun `已存在且校验通过的文件会跳过`() = runTest {
        val target = root.resolve("libraries/a/b.jar")
        Files.createDirectories(target.parent)
        Files.write(target, okBytes)
        // 故意给一个连不上的地址：如果还在请求就能发现
        val file = PlannedFile("libraries/a/b.jar", "http://127.0.0.1:1/unreachable", okSha, okBytes.size.toLong())

        val report = downloader().download(root, listOf(file))

        assertEquals(1, report.skipped)
        assertEquals(0, report.downloaded)
        assertTrue(report.isSuccess)
    }

    @Test
    fun `SHA-1 不符时失败且不留文件`() = runTest {
        val file = PlannedFile("b.jar", "$base/bad.jar", okSha, okBytes.size.toLong())

        val report = downloader().download(root, listOf(file))

        assertEquals(1, report.failures.size)
        assertTrue(report.failures.single().error.message.orEmpty().contains("SHA-1"))
        assertFalse(Files.exists(root.resolve("b.jar")))
        assertFalse(Files.exists(root.resolve("b.jar.mmcl-tmp")))
    }

    @Test
    fun `HTTP 404 记为失败`() = runTest {
        val file = PlannedFile("c.jar", "$base/missing.jar")

        val report = downloader().download(root, listOf(file))

        assertEquals(1, report.failures.size)
        assertFalse(Files.exists(root.resolve("c.jar")))
    }

    @Test
    fun `拒绝路径穿越`() = runTest {
        val file = PlannedFile("../evil.jar", "$base/ok.jar", okSha)

        val report = downloader().download(root, listOf(file))

        assertEquals(1, report.failures.size)
        assertFalse(Files.exists(root.parent.resolve("evil.jar")))
    }

    @Test
    fun `进度回调会报告完成数`() = runTest {
        val files =
            listOf(
                PlannedFile("a.jar", "$base/ok.jar", okSha),
                PlannedFile("b.jar", "$base/ok.jar", okSha),
            )
        val events = mutableListOf<DownloadProgress>()

        val report = downloader(concurrency = 1).download(root, files) { events += it }

        assertEquals(2, report.downloaded)
        assertEquals(2, events.size)
        assertEquals(listOf(1, 2), events.map { it.completed })
        assertTrue(events.all { it.total == 2 })
    }
}
