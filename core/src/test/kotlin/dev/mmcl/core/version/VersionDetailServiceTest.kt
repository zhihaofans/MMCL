package dev.mmcl.core.version

import dev.mmcl.core.net.HttpTransport
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class VersionDetailServiceTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var dir: Path

    private val entry =
        VersionManifestEntry(
            id = "1.21.8",
            type = "release",
            url = "https://example.invalid/1.21.8.json",
            sha1 = "abc",
            releaseTime = "2026-07-15T09:00:00+00:00",
        )

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("mmcl-detail-test")
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun detailFile(): Path = dir.resolve("versions").resolve("1.21.8").resolve("1.21.8.json")

    private fun service(transport: HttpTransport): VersionDetailService =
        VersionDetailService(dir.resolve("versions"), transport, logger)

    private class FakeTransport(private val body: String) : HttpTransport {
        var textCalls: Int = 0
            private set
        var failure: Throwable? = null

        override suspend fun getText(url: String): String {
            textCalls++
            failure?.let { throw it }
            return body
        }

        override suspend fun downloadTo(url: String, target: Path): Unit = error("下载接口不应被调用")
    }

    @Test
    fun `首次加载会拉取并落盘`() = runTest {
        val transport = FakeTransport(SAMPLE_DETAIL)

        val detail = service(transport).load(entry)

        assertEquals("1.21.8", detail.id)
        assertEquals("net.minecraft.client.main.Main", detail.mainClass)
        assertEquals(1, transport.textCalls)
        assertTrue(Files.isRegularFile(detailFile()))
        assertFalse(Files.exists(detailFile().resolveSibling("1.21.8.json.mmcl-tmp")))
    }

    @Test
    fun `第二次加载命中本地副本`() = runTest {
        val transport = FakeTransport(SAMPLE_DETAIL)
        service(transport).load(entry)
        service(transport).load(entry)

        assertEquals(1, transport.textCalls)
    }

    @Test
    fun `forceRefresh 会重新拉取`() = runTest {
        val transport = FakeTransport(SAMPLE_DETAIL)
        val service = service(transport)

        service.load(entry)
        service.load(entry, forceRefresh = true)

        assertEquals(2, transport.textCalls)
    }

    @Test
    fun `没有详情地址时报错`() = runTest {
        assertFailsWith<VersionDetailException> {
            service(FakeTransport(SAMPLE_DETAIL)).load(entry.copy(url = ""))
        }
    }

    @Test
    fun `坏 JSON 报错且不落盘`() = runTest {
        val transport = FakeTransport("not a version detail")

        assertFailsWith<VersionDetailException> { service(transport).load(entry) }
        assertFalse(Files.exists(detailFile()))
    }

    @Test
    fun `本地坏缓存会被重新拉取覆盖`() = runTest {
        Files.createDirectories(detailFile().parent)
        Files.writeString(detailFile(), "{ broken")
        val transport = FakeTransport(SAMPLE_DETAIL)

        val detail = service(transport).load(entry)

        assertEquals(1, transport.textCalls)
        assertEquals("1.21.8", detail.id)
    }

    @Test
    fun `网络异常会包装成详情异常`() = runTest {
        val transport = FakeTransport(SAMPLE_DETAIL).apply { failure = java.io.IOException("断网") }

        assertFailsWith<VersionDetailException> { service(transport).load(entry) }
    }

    @Test
    fun `解析出客户端 资源索引 与库`() = runTest {
        val detail = service(FakeTransport(SAMPLE_DETAIL)).load(entry)

        assertEquals("https://example.invalid/client.jar", detail.client?.url)
        assertEquals("17", detail.assetIndex?.id)
        assertEquals("client-1.21.8.xml", detail.logging?.client?.file?.id)
        assertEquals(2, detail.libraries.size)
        assertEquals(21, detail.javaVersion?.majorVersion)
        assertTrue(URL(detail.client!!.url).protocol == "https")
    }

    private companion object {
        const val SAMPLE_DETAIL: String =
            """
            {
              "id": "1.21.8",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "17",
              "assetIndex": {
                "id": "17",
                "sha1": "asset-index-sha",
                "size": 10,
                "url": "https://example.invalid/assets/17.json"
              },
              "downloads": {
                "client": {
                  "sha1": "client-sha",
                  "size": 100,
                  "url": "https://example.invalid/client.jar"
                }
              },
              "libraries": [
                {
                  "name": "com.mojang:brigadier:1.3.10",
                  "downloads": {
                    "artifact": {
                      "path": "com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar",
                      "sha1": "lib-sha",
                      "size": 1,
                      "url": "https://example.invalid/lib.jar"
                    }
                  }
                },
                {
                  "name": "org.lwjgl:lwjgl:3.3.3",
                  "rules": [{ "action": "allow", "os": { "name": "osx" } }],
                  "downloads": {
                    "artifact": {
                      "path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                      "sha1": "lib2-sha",
                      "size": 2,
                      "url": "https://example.invalid/lib2.jar"
                    }
                  }
                }
              ],
              "logging": {
                "client": {
                  "argument": "-Dlog4j.configurationFile=${'$'}{path}",
                  "file": {
                    "id": "client-1.21.8.xml",
                    "sha1": "log-sha",
                    "size": 5,
                    "url": "https://example.invalid/log.xml"
                  },
                  "type": "log4j2-xml"
                }
              },
              "javaVersion": { "component": "java-runtime-delta", "majorVersion": 21 }
            }
            """
    }
}
