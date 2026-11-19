package dev.mmcl.core.download

import com.sun.net.httpserver.HttpServer
import dev.mmcl.core.net.JdkHttpTransport
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import dev.mmcl.core.net.HttpTransport
import java.io.IOException
import java.nio.file.StandardOpenOption
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
        server.createContext("/mirror-404.jar") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.createContext("/origin-ok.jar") { exchange ->
            exchange.sendResponseHeaders(200, okBytes.size.toLong())
            exchange.responseBody.use { it.write(okBytes) }
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
    fun `重试时从已下载长度续传`() = runTest {
        val content = ("hello-mmcl-resume-" + "x".repeat(4000)).toByteArray()
        val expectedSha = sha1Hex(content)
        var attempts = 0
        var resumedFrom = -1L
        val transport =
            object : HttpTransport {
                override suspend fun getText(url: String): String = ""

                override suspend fun downloadTo(url: String, target: Path) {
                    error("不该整文件重下")
                }

                override suspend fun downloadTo(url: String, target: Path, onBytes: suspend (Int) -> Unit) {
                    error("不该整文件重下")
                }

                override suspend fun resumeTo(url: String, target: Path, onBytes: suspend (Int) -> Unit): Boolean {
                    attempts++
                    if (attempts == 1) {
                        // 第一次只下了一千字节就断网
                        Files.write(target, content.copyOf(1000))
                        throw IOException("连接中断")
                    }
                    resumedFrom = Files.size(target)
                    Files.write(target, content.copyOfRange(1000, content.size), StandardOpenOption.APPEND)
                    onBytes(content.size - 1000)
                    return true
                }
            }
        val downloader =
            FileDownloader(transport, logger, concurrency = 1, maxAttempts = 3, retryDelayMillis = { 0 })
        val file = PlannedFile("big.jar", "https://example.invalid/big.jar", expectedSha, content.size.toLong())

        val report = downloader.download(root, listOf(file))

        assertTrue(report.isSuccess, report.failures.firstOrNull()?.error?.message.orEmpty())
        assertEquals(2, attempts)
        assertEquals(1000L, resumedFrom)
        assertTrue(content.contentEquals(Files.readAllBytes(root.resolve("big.jar"))))
    }

    @Test
    fun `镜像地址失败时回退原地址并成功`() = runTest {
        val file =
            PlannedFile(
                relativePath = "libraries/fallback.jar",
                url = "$base/mirror-404.jar",
                sha1 = okSha,
                size = okBytes.size.toLong(),
                fallbackUrl = "$base/origin-ok.jar",
            )

        val report = FileDownloader(JdkHttpTransport(), logger, retryDelayMillis = { 0 }).download(root, listOf(file))

        assertTrue(
            report.failures.isEmpty(),
            "不应失败：${report.failures.map { it.file.relativePath to it.error.message }}",
        )
        assertEquals(1, report.downloaded)
        assertTrue(Files.readAllBytes(root.resolve("libraries/fallback.jar")).contentEquals(okBytes))
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
