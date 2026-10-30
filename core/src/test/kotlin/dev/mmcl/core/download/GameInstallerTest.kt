package dev.mmcl.core.download

import com.sun.net.httpserver.HttpServer
import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.net.JdkHttpTransport
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.core.version.VersionManifestEntry
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

/**
 * 端到端测试：本地 HTTP 服务器扮演"来源"，覆盖 详情拉取 → 文件规划 → 下载 → SHA-1 校验 全链路。
 */
class GameInstallerTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var dir: Path
    private lateinit var server: HttpServer
    private lateinit var base: String

    private val clientBytes = "fake-client-jar".toByteArray()
    private val libraryBytes = "fake-library".toByteArray()
    private val clientSha = sha1Hex(clientBytes)
    private val librarySha = sha1Hex(libraryBytes)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("mmcl-installer-test")
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/client.jar") { exchange ->
            exchange.sendResponseHeaders(200, clientBytes.size.toLong())
            exchange.responseBody.use { it.write(clientBytes) }
        }
        server.createContext("/lib.jar") { exchange ->
            exchange.sendResponseHeaders(200, libraryBytes.size.toLong())
            exchange.responseBody.use { it.write(libraryBytes) }
        }
        server.createContext("/1.21.8.json") { exchange ->
            val body = detailJson().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.toFile().deleteRecursively()
    }

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun detailJson(): String =
        """
        {
          "id": "1.21.8",
          "type": "release",
          "mainClass": "net.minecraft.client.main.Main",
          "downloads": {
            "client": { "sha1": "$clientSha", "size": ${clientBytes.size}, "url": "$base/client.jar" }
          },
          "libraries": [
            {
              "name": "com.example:lib:1.0",
              "downloads": {
                "artifact": {
                  "path": "com/example/lib/1.0/lib-1.0.jar",
                  "sha1": "$librarySha",
                  "size": ${libraryBytes.size},
                  "url": "$base/lib.jar"
                }
              }
            }
          ]
        }
        """.trimIndent()

    private fun entry() =
        VersionManifestEntry(
            id = "1.21.8",
            type = "release",
            url = "$base/1.21.8.json",
        )

    private fun installer(transport: JdkHttpTransport = JdkHttpTransport(userAgent = "test")) =
        GameInstaller(
            detailService = VersionDetailService(dir.resolve("versions"), transport, logger),
            downloader = FileDownloader(transport, logger, concurrency = 2),
            root = dir,
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
            logger = logger,
        )

    @Test
    fun `规划只列文件不写盘`() = runTest {
        val plan = installer().plan(entry())

        assertEquals("1.21.8", plan.versionId)
        assertEquals(2, plan.fileCount)
        assertEquals((clientBytes.size + libraryBytes.size).toLong(), plan.totalBytes)
        assertTrue(!Files.exists(dir.resolve("versions/1.21.8/1.21.8.jar")))
    }

    @Test
    fun `安装会拉全文件并通过校验`() = runTest {
        val result = installer().install(entry())

        assertTrue(result.report.isSuccess)
        assertEquals(2, result.report.downloaded)
        assertEquals(String(clientBytes), Files.readString(dir.resolve("versions/1.21.8/1.21.8.jar")))
        assertEquals(String(libraryBytes), Files.readString(dir.resolve("libraries/com/example/lib/1.0/lib-1.0.jar")))
    }

    @Test
    fun `重复安装会跳过已校验文件`() = runTest {
        installer().install(entry())

        val second = installer().install(entry())

        assertEquals(2, second.report.skipped)
        assertEquals(0, second.report.downloaded)
    }

    @Test
    fun `进度回调覆盖全部文件`() = runTest {
        val events = mutableListOf<DownloadProgress>()

        installer().install(entry()) { events += it }

        assertEquals(2, events.size)
        assertEquals(listOf(1, 2), events.map { it.completed })
    }
}
