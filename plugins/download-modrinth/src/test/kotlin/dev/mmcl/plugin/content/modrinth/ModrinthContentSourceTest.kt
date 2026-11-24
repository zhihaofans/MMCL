package dev.mmcl.plugin.content.modrinth

import dev.mmcl.api.capability.ContentKind
import dev.mmcl.api.capability.ContentSearchQuery
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class ModrinthContentSourceTest {
    private val logger = LoggerFactory.getLogger("test.modrinth")

    private class FakeHttp(private val handler: suspend (String) -> String) : ModrinthHttp {
        val calls = mutableListOf<String>()

        override suspend fun getText(url: String): String {
            calls += url
            return handler(url)
        }
    }

    private fun source(handler: suspend (String) -> String): Pair<ModrinthContentSource, FakeHttp> {
        val http = FakeHttp(handler)
        return ModrinthContentSource(ModrinthClient(http, logger)) to http
    }

    @Test
    fun `搜索按项目类型 加载器 版本构造 facets`() = runTest {
        val json =
            """
            {"hits":[{"project_id":"P1","slug":"sodium","title":"Sodium",
              "description":"Rendering","downloads":123}]}
            """.trimIndent()
        val (source, http) = source { json }

        val results =
            source.search(
                ContentSearchQuery(query = "sodium", gameVersion = "26.3", loader = "fabric", kind = ContentKind.MOD),
            )

        val url = http.calls.single()
        assertTrue(url.startsWith("https://api.modrinth.com/v2/search?"))
        assertTrue(url.contains("query=sodium"))
        assertTrue(url.contains("project_type%3Amod"))
        assertTrue(url.contains("categories%3Afabric"))
        assertTrue(url.contains("versions%3A26.3"))
        assertEquals("P1", results.single().id)
        assertEquals("Sodium", results.single().title)
    }

    @Test
    fun `版本映射文件哈希与依赖类型`() = runTest {
        val json =
            """
            [{"id":"V1","project_id":"P1","name":"Sodium 1.0","version_number":"1.0",
              "game_versions":["26.3"],"loaders":["fabric"],
              "files":[{"url":"https://cdn.example/sodium.jar","filename":"sodium.jar","size":99,
                        "primary":true,"hashes":{"sha1":"abc","sha512":"def"}}],
              "dependencies":[
                {"version_id":"API1","project_id":"API","dependency_type":"required"},
                {"version_id":"OPT1","project_id":"OPT","dependency_type":"optional"}]}]
            """.trimIndent()
        val (source, http) = source { json }

        val versions = source.versions("P1", "26.3", "fabric")

        assertTrue(http.calls.single().contains("/v2/project/P1/version?"))
        val version = versions.single()
        assertEquals("V1", version.id)
        assertEquals("sodium.jar", version.files.single().fileName)
        assertEquals("abc", version.files.single().sha1)
        assertEquals("def", version.files.single().sha512)
        assertEquals(2, version.dependencies.size)
        assertTrue(version.dependencies[0].required)
        assertTrue(!version.dependencies[1].required)
    }

    @Test
    fun `按 id 取版本 404 返回 null`() = runTest {
        val (source, _) =
            source {
                throw ModrinthHttpException(404, "not found")
            }

        assertNull(source.version("missing"))
    }

    @Test
    fun `取版本失败时抛 IO 异常`() = runTest {
        val (source, _) =
            source {
                throw ModrinthHttpException(500, "server error")
            }

        var thrown = false
        try {
            source.version("boom")
        } catch (e: IOException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `插件要求启动器至少 0_0_8`() {
        assertEquals("0.0.8", ModrinthPlugin().minLauncherVersion)
    }
}
