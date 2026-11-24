package dev.mmcl.core.content

import dev.mmcl.api.capability.ContentDependency
import dev.mmcl.api.capability.ContentFile
import dev.mmcl.api.capability.ContentProject
import dev.mmcl.api.capability.ContentSearchQuery
import dev.mmcl.api.capability.ContentSource
import dev.mmcl.api.capability.ContentVersion
import dev.mmcl.core.download.FileDownloader
import dev.mmcl.core.net.HttpTransport
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class ContentInstallerTest {
    private val logger = LoggerFactory.getLogger("test.content")
    private lateinit var instanceDir: Path

    @BeforeTest
    fun setUp() {
        instanceDir = Files.createTempDirectory("mmcl-content-test")
    }

    @AfterTest
    fun tearDown() {
        instanceDir.toFile().deleteRecursively()
    }

    private class FakeTransport : HttpTransport {
        override suspend fun getText(url: String): String = error("不应调用 getText")

        override suspend fun downloadTo(url: String, target: Path) {
            Files.createDirectories(target.parent)
            Files.writeString(target, "content of $url")
        }
    }

    private class FakeSource(private val versions: Map<String, ContentVersion>) : ContentSource {
        override val id: String = "fake"
        override val displayName: String = "Fake"

        override suspend fun search(query: ContentSearchQuery): List<ContentProject> = emptyList()

        override suspend fun versions(projectId: String, gameVersion: String, loader: String?): List<ContentVersion> =
            versions.values.filter { it.projectId == projectId && gameVersion in it.gameVersions }

        override suspend fun version(versionId: String): ContentVersion? = versions[versionId]
    }

    private fun file(name: String) = ContentFile(fileName = name, url = "https://example.invalid/$name", size = 10)

    private fun version(
        id: String,
        project: String,
        fileName: String,
        vararg dependencies: ContentDependency,
    ) = ContentVersion(
        id = id,
        projectId = project,
        versionNumber = "1.0",
        gameVersions = listOf("26.3"),
        loaders = listOf("fabric"),
        files = listOf(file(fileName)),
        dependencies = dependencies.toList(),
    )

    private fun installer(source: ContentSource) =
        ContentInstaller(FileDownloader(FakeTransport(), logger), logger).let { installer ->
            installer to source
        }

    @Test
    fun `安装主文件与必需依赖`() = runTest {
        val api = version("api-1", "fabric-api", "fabric-api.jar")
        val main =
            version(
                "mod-1",
                "example-mod",
                "example-mod.jar",
                ContentDependency(projectId = "fabric-api", required = true),
            )
        val source = FakeSource(mapOf(api.id to api, main.id to main))
        val installer = ContentInstaller(FileDownloader(FakeTransport(), logger), logger)

        val result =
            installer.install(
                ContentInstallRequest(
                    source = source,
                    instanceDir = instanceDir,
                    gameVersion = "26.3",
                    loader = "fabric",
                    version = main,
                ),
            )

        assertEquals(listOf("mod-1", "api-1"), result.versions.map { it.id })
        assertTrue(Files.isRegularFile(instanceDir.resolve("mods/example-mod.jar")))
        assertTrue(Files.isRegularFile(instanceDir.resolve("mods/fabric-api.jar")))
        assertEquals(2, result.report.downloaded)
    }

    @Test
    fun `已存在同名文件会跳过而不是覆盖`() = runTest {
        val main = version("mod-1", "example-mod", "example-mod.jar")
        Files.createDirectories(instanceDir.resolve("mods"))
        Files.writeString(instanceDir.resolve("mods/example-mod.jar"), "user's own copy")
        val source = FakeSource(mapOf(main.id to main))
        val installer = ContentInstaller(FileDownloader(FakeTransport(), logger), logger)

        val result =
            installer.install(
                ContentInstallRequest(source, instanceDir, "26.3", "fabric", main),
            )

        assertEquals(listOf("example-mod.jar"), result.skippedExisting)
        assertEquals("user's own copy", Files.readString(instanceDir.resolve("mods/example-mod.jar")))
    }

    @Test
    fun `依赖循环会终止且不重复`() = runTest {
        val a =
            version(
                "a-1",
                "a",
                "a.jar",
                ContentDependency(projectId = "b", required = true),
            )
        val b =
            version(
                "b-1",
                "b",
                "b.jar",
                ContentDependency(projectId = "a", required = true),
            )
        val source = FakeSource(mapOf(a.id to a, b.id to b))
        val installer = ContentInstaller(FileDownloader(FakeTransport(), logger), logger)

        val result =
            installer.install(
                ContentInstallRequest(source, instanceDir, "26.3", "fabric", a),
            )

        assertEquals(listOf("a-1", "b-1"), result.versions.map { it.id })
        assertEquals(2, result.versions.size)
    }
}
