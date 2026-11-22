package dev.mmcl.plugin.loader.fabric

import dev.mmcl.api.capability.LoaderInstallRequest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class FabricInstallerTest {
    private val logger = LoggerFactory.getLogger("test.fabric")

    private val listJson =
        """
        [
          {"loader":{"version":"0.21.0-beta.1","stable":false}},
          {"loader":{"version":"0.20.1","stable":true}}
        ]
        """.trimIndent()

    private val profileJson =
        """
        {"id":"fabric-loader-0.20.1-26.3","inheritsFrom":"26.3",
         "mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
         "libraries":[{"name":"net.fabricmc:fabric-loader:0.20.1","url":"https://maven.fabricmc.net/","sha1":"abc123","size":123}],
         "arguments":{"jvm":["-DFabricMcEmu= net.minecraft.client.main.Main "]}}
        """.trimIndent()

    private class FakeHttp(private val responses: Map<String, String>) : FabricHttp {
        val calls = mutableListOf<String>()

        override suspend fun getText(url: String): String {
            calls += url
            return responses[url] ?: throw IOException("404 $url")
        }
    }

    private fun installer(
        http: FabricHttp,
        mirrors: List<dev.mmcl.api.capability.UrlMirror> = emptyList(),
    ) = FabricInstaller(FabricMetaClient(http, logger), http, { mirrors }, logger)

    private fun urls(base: String, game: String = "26.3", loader: String = "0.20.1") =
        "$base/v2/versions/loader/$game" to "$base/v2/versions/loader/$game/$loader/profile/json"

    @Test
    fun `列表解析出 stable 标记且官方优先`() = runTest {
        val (listUrl, _) = urls(FabricMetaClient.OFFICIAL_META)
        val http = FakeHttp(mapOf(listUrl to listJson))

        val versions = installer(http).listVersions("26.3")

        assertEquals(listOf("0.21.0-beta.1", "0.20.1"), versions.map { it.version })
        assertEquals(false, versions.first().stable)
        assertEquals(true, versions.last().stable)
        assertTrue(http.calls.single().startsWith(FabricMetaClient.OFFICIAL_META))
    }

    @Test
    fun `默认选最新稳定版并返回 overlay`() = runTest {
        val (listUrl, profileUrl) = urls(FabricMetaClient.OFFICIAL_META)
        val http = FakeHttp(mapOf(listUrl to listJson, profileUrl to profileJson))

        val result = installer(http).install(LoaderInstallRequest("26.3"))

        assertEquals("fabric-loader-0.20.1-26.3", result.versionId)
        assertTrue(result.detailJson.contains("\"downloads\""))
        assertTrue(result.detailJson.contains("\"sha1\":\"abc123\""))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `官方失败时回退镜像并带警告`() = runTest {
        val (_, mirrorProfileUrl) = urls(FabricMetaClient.MIRROR_META)
        val (_, officialProfileUrl) = urls(FabricMetaClient.OFFICIAL_META)
        val (mirrorListUrl) = urls(FabricMetaClient.MIRROR_META)
        val (officialListUrl) = urls(FabricMetaClient.OFFICIAL_META)
        val http =
            FakeHttp(
                mapOf(
                    mirrorListUrl to listJson,
                    mirrorProfileUrl to profileJson,
                ),
            )

        val result = installer(http).install(LoaderInstallRequest("26.3"))

        assertTrue(http.calls.contains(officialListUrl))
        assertTrue(http.calls.contains(officialProfileUrl))
        assertTrue(result.warnings.any { it.contains("BMCLAPI") })
    }

    @Test
    fun `inheritsFrom 不匹配时报错`() = runTest {
        val (listUrl, profileUrl) = urls(FabricMetaClient.OFFICIAL_META)
        val bad = profileJson.replace("\"inheritsFrom\":\"26.3\"", "\"inheritsFrom\":\"26.2\"")
        val http = FakeHttp(mapOf(listUrl to listJson, profileUrl to bad))

        val error = assertFailsWith<IOException> { installer(http).install(LoaderInstallRequest("26.3")) }

        assertTrue(error.message!!.contains("inheritsFrom"))
    }

    @Test
    fun `缺少 mainClass 时报错`() = runTest {
        val (listUrl, profileUrl) = urls(FabricMetaClient.OFFICIAL_META)
        val bad = """{"id":"fabric-loader-x","inheritsFrom":"26.3","libraries":[{"name":"a:b:1"}]}"""
        val http = FakeHttp(mapOf(listUrl to listJson, profileUrl to bad))

        val error = assertFailsWith<IOException> { installer(http).install(LoaderInstallRequest("26.3")) }

        assertTrue(error.message!!.contains("mainClass"))
    }

    @Test
    fun `库地址会被镜像改写并写入 downloads`() = runTest {
        val (listUrl, profileUrl) = urls(FabricMetaClient.OFFICIAL_META)
        val http = FakeHttp(mapOf(listUrl to listJson, profileUrl to profileJson))
        val mirror =
            object : dev.mmcl.api.capability.UrlMirror {
                override val id: String = "test"

                override fun resolve(url: String): String? =
                    if (url.startsWith("https://maven.fabricmc.net/")) {
                        "https://mirror.invalid/maven/" + url.removePrefix("https://maven.fabricmc.net/")
                    } else {
                        null
                    }
            }

        val result = installer(http, listOf(mirror)).install(LoaderInstallRequest("26.3"))

        assertTrue(
            result.detailJson.contains(
                "https://mirror.invalid/maven/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar",
            ),
        )
        assertTrue(result.detailJson.contains("\"sha1\":\"abc123\""))
    }

    @Test
    fun `缺少 sha1 时从官方 sidecar 补齐`() = runTest {
        val (listUrl, profileUrl) = urls(FabricMetaClient.OFFICIAL_META)
        val sidecarUrl =
            "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar.sha1"
        val profile = profileJson.replace(",\"sha1\":\"abc123\",\"size\":123", "")
        val http = FakeHttp(mapOf(listUrl to listJson, profileUrl to profile, sidecarUrl to "deadbeef"))

        val result = installer(http).install(LoaderInstallRequest("26.3"))

        assertTrue(result.detailJson.contains("\"sha1\":\"deadbeef\""))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `sidecar 也拿不到时只警告不失败`() = runTest {
        val (listUrl, profileUrl) = urls(FabricMetaClient.OFFICIAL_META)
        val profile = profileJson.replace(",\"sha1\":\"abc123\",\"size\":123", "")
        val http = FakeHttp(mapOf(listUrl to listJson, profileUrl to profile))

        val result = installer(http).install(LoaderInstallRequest("26.3"))

        assertTrue(result.warnings.any { it.contains("没有哈希") })
    }

    @Test
    fun `指定不存在的 loader 版本时报错`() = runTest {
        val (listUrl, _) = urls(FabricMetaClient.OFFICIAL_META)
        val http = FakeHttp(mapOf(listUrl to listJson))

        assertFailsWith<IOException> {
            installer(http).install(LoaderInstallRequest("26.3", loaderVersion = "9.9.9"))
        }
    }
}
