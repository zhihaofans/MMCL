package dev.mmcl.plugin.runtime.foojay

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.ArchiveFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FoojayRuntimeProviderTest {
    private var requestedUrl: String? = null

    private fun provider(body: String, distribution: String = "zulu") =
        FoojayRuntimeProvider(
            distribution = distribution,
            displayName = "Azul Zulu",
            priority = 110,
            fetchJson = { url ->
                requestedUrl = url
                body
            },
        )

    private fun response(fileName: String, redirect: String) =
        """
        {"result":[{
          "filename":"$fileName",
          "distribution":"zulu",
          "links":{"pkg_download_redirect":"$redirect"}
        }],"message":"1 package(s) found"}
        """.trimIndent()

    @Test
    fun `Windows 请求 zip，且把目标平台作为查询参数传给 API`() = runTest {
        val download =
            provider(response("zulu21.zip", "https://api.foojay.io/disco/v3.0/ids/abc/redirect"))
                .resolve(21, OperatingSystem.WINDOWS, Architecture.X86_64)

        val url = requestedUrl.orEmpty()
        assertTrue(url.contains("version=21"), url)
        assertTrue(url.contains("operating_system=windows"), url)
        assertTrue(url.contains("architecture=x64"), url)
        assertTrue(url.contains("archive_type=zip"), url)
        assertTrue(url.contains("distribution=zulu"), url)
        assertEquals(ArchiveFormat.ZIP, download?.format)
        assertEquals("zulu21.zip", download?.fileName)
        assertEquals("https://api.foojay.io/disco/v3.0/ids/abc/redirect", download?.url)
    }

    @Test
    fun `macOS arm64 请求 tar_gz`() = runTest {
        val download =
            provider(response("zulu21.tar.gz", "https://api.foojay.io/disco/v3.0/ids/abc/redirect"))
                .resolve(21, OperatingSystem.MACOS, Architecture.ARM64)

        val url = requestedUrl.orEmpty()
        assertTrue(url.contains("operating_system=mac"), url)
        assertTrue(url.contains("architecture=aarch64"), url)
        assertTrue(url.contains("archive_type=tar.gz"), url)
        assertEquals(ArchiveFormat.TAR_GZ, download?.format)
    }

    @Test
    fun `未知架构不发请求`() = runTest {
        val download =
            provider(response("x.zip", "https://example.invalid/x.zip"))
                .resolve(21, OperatingSystem.LINUX, Architecture.UNKNOWN)

        assertNull(download)
        assertNull(requestedUrl)
    }

    @Test
    fun `API 没有该平台的包时返回 null`() = runTest {
        val download =
            provider("""{"result":[],"message":"0 package(s) found"}""")
                .resolve(21, OperatingSystem.WINDOWS, Architecture.ARM64)

        assertNull(download)
    }

    @Test
    fun `响应不是 JSON 时返回 null 而不是抛异常`() = runTest {
        val download = provider("<html>403 Forbidden</html>").resolve(21, OperatingSystem.LINUX, Architecture.X86_64)

        assertNull(download)
    }

    @Test
    fun `只有 sha1 类型的校验和才会被采用`() {
        val provider = provider("")
        val sha256Body =
            """
            {"result":[{"filename":"a.zip",
              "checksum":"deadbeef","checksum_type":"sha256",
              "links":{"pkg_download_redirect":"https://example.invalid/a.zip"}}]}
            """.trimIndent()
        assertNull(provider.parse(sha256Body, ArchiveFormat.ZIP)?.sha1)

        val sha1Body =
            """
            {"result":[{"filename":"a.zip",
              "checksum":"deadbeef","checksum_type":"sha1",
              "links":{"pkg_download_redirect":"https://example.invalid/a.zip"}}]}
            """.trimIndent()
        assertEquals("deadbeef", provider.parse(sha1Body, ArchiveFormat.ZIP)?.sha1)
    }

    @Test
    fun `一个插件注册多个发行版，id 带前缀`() {
        val ids = FoojayDistributions.ALL.map { distribution ->
            FoojayRuntimeProvider(distribution.distribution, distribution.displayName).id
        }

        assertEquals(FoojayDistributions.ALL.size, ids.size)
        assertTrue(ids.all { it.startsWith("foojay:") }, ids.toString())
        assertTrue(ids.none { it == "foojay:temurin" }, "Temurin 由 runtime-adoptium 提供，这里不应重复")
    }
}
