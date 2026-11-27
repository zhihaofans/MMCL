package dev.mmcl.plugin.update.checker

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class UpdateCheckerTest {
    private val logger = LoggerFactory.getLogger("test.update")
    private lateinit var root: Path
    private lateinit var pluginsDir: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("mmcl-update-test")
        pluginsDir = root.resolve("plugins")
        Files.createDirectories(pluginsDir)
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private class FakeHttp(
        private val tag: String,
        private val index: String?,
        private val downloads: Map<String, String> = emptyMap(),
    ) : UpdateHttp {
        override suspend fun latestTag(releasesLatestUrl: String): String = tag

        override suspend fun getText(url: String): String = index ?: throw IOException("404")

        override suspend fun download(url: String, target: Path) {
            Files.writeString(target, downloads[url] ?: throw IOException("404 $url"))
        }
    }

    private fun checker(http: UpdateHttp, current: String = "0.0.7") =
        UpdateChecker(http, current, pluginsDir, repoBase = "https://example.invalid/repo", logger = logger)

    @Test
    fun `检查启动器与插件更新`() = runTest {
        Files.writeString(pluginsDir.resolve("dev.mmcl.loader.fabric-0.0.2.jar"), "old")
        Files.writeString(pluginsDir.resolve("dev.mmcl.locale.zh_tw-0.0.2.jar"), "same")
        val index =
            """
            {"launcher":{"version":"0.0.8","tag":"0.0.8","releaseUrl":"https://example.invalid/release"},
             "plugins":[
               {"id":"dev.mmcl.loader.fabric","version":"0.0.3","file":"dev.mmcl.loader.fabric-0.0.3.jar","sha256":"x"},
               {"id":"dev.mmcl.locale.zh_tw","version":"0.0.2","file":"dev.mmcl.locale.zh_tw-0.0.2.jar","sha256":"y"}
             ]}
            """.trimIndent()
        val result = checker(FakeHttp("0.0.8", index)).check()

        assertTrue(result.launcherUpdateAvailable)
        assertEquals("0.0.8", result.latestVersion)
        assertTrue(result.indexAvailable)
        val fabric = result.plugins.first { it is PluginState.UpdateAvailable && it.remote.id == "dev.mmcl.loader.fabric" }
        assertEquals("0.0.2", (fabric as PluginState.UpdateAvailable).installed?.version)
        assertTrue(result.plugins.any { it is PluginState.UpToDate && it.remote.id == "dev.mmcl.locale.zh_tw" })
    }

    @Test
    fun `索引缺失时仍可检查启动器`() = runTest {
        val result = checker(FakeHttp("0.0.8", null)).check()

        assertTrue(result.launcherUpdateAvailable)
        assertFalse(result.indexAvailable)
        assertTrue(result.plugins.isEmpty())
    }

    @Test
    fun `安装插件校验哈希并删除旧版本`() = runTest {
        Files.writeString(pluginsDir.resolve("dev.mmcl.loader.fabric-0.0.2.jar"), "old")
        val body = "new-jar"
        val remote =
            PluginDto(
                id = "dev.mmcl.loader.fabric",
                version = "0.0.3",
                file = "dev.mmcl.loader.fabric-0.0.3.jar",
                sha256 = sha256(body),
            )
        val url = "https://example.invalid/repo/releases/download/0.0.8/${remote.file}"

        val target = checker(FakeHttp("0.0.8", null, mapOf(url to body))).install(remote, "0.0.8").getOrThrow()

        assertEquals("new-jar", Files.readString(target))
        assertFalse(Files.exists(pluginsDir.resolve("dev.mmcl.loader.fabric-0.0.2.jar")))
    }

    @Test
    fun `哈希不匹配时拒绝安装`() = runTest {
        val body = "evil"
        val remote =
            PluginDto(
                id = "dev.mmcl.loader.fabric",
                version = "0.0.3",
                file = "dev.mmcl.loader.fabric-0.0.3.jar",
                sha256 = sha256("expected"),
            )
        val url = "https://example.invalid/repo/releases/download/0.0.8/${remote.file}"

        val result = checker(FakeHttp("0.0.8", null, mapOf(url to body))).install(remote, "0.0.8")

        assertTrue(result.isFailure)
        assertFalse(Files.exists(pluginsDir.resolve(remote.file)))
    }

    @Test
    fun `解析插件文件名与版本比较`() {
        assertEquals("dev.mmcl.loader.fabric", pluginFileInfo("dev.mmcl.loader.fabric-0.0.3.jar")?.id)
        assertEquals("0.0.3", pluginFileInfo("dev.mmcl.loader.fabric-0.0.3.jar")?.version)
        assertEquals(null, pluginFileInfo("not-a-plugin.jar"))
        assertTrue(compareVersions("0.0.10", "0.0.9") > 0)
        assertEquals(0, compareVersions("0.0.3", "v0.0.3"))
    }
}
