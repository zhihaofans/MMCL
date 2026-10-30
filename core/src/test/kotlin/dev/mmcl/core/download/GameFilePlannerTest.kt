package dev.mmcl.core.download

import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.version.AssetIndexRef
import dev.mmcl.core.version.DownloadEntry
import dev.mmcl.core.version.Library
import dev.mmcl.core.version.LibraryDownloads
import dev.mmcl.core.version.LibraryRule
import dev.mmcl.core.version.Logging
import dev.mmcl.core.version.LoggingClient
import dev.mmcl.core.version.OsRule
import dev.mmcl.core.version.VersionDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameFilePlannerTest {
    private fun detail(vararg libraries: Library) =
        VersionDetail(
            id = "1.21.8",
            mainClass = "net.minecraft.client.main.Main",
            assetIndex =
                AssetIndexRef(
                    id = "17",
                    sha1 = "index-sha",
                    size = 10,
                    url = "https://example.invalid/assets/17.json",
                ),
            downloads =
                mapOf(
                    "client" to
                        DownloadEntry(
                            url = "https://example.invalid/client.jar",
                            sha1 = "client-sha",
                            size = 100,
                        ),
                ),
            libraries = libraries.toList(),
            logging = Logging(client = LoggingClient(file = DownloadEntry(url = "https://example.invalid/log.xml", sha1 = "log-sha", size = 5))),
        )

    private fun library(
        name: String,
        path: String? = "com/example/${name.substringAfter(':').substringBefore(':')}/1.0/lib.jar",
        rules: List<LibraryRule> = emptyList(),
        artifact: Boolean = true,
    ) = Library(
        name = name,
        downloads =
            LibraryDownloads(
                artifact = if (artifact) DownloadEntry(url = "https://example.invalid/lib.jar", sha1 = "lib-sha", size = 1, path = path) else null,
            ),
        rules = rules,
    )

    @Test
    fun `基础文件包含客户端 资源索引 日志配置`() {
        val plan = GameFilePlanner.plan(detail(), OperatingSystem.MACOS)

        assertEquals(
            listOf(
                "assets/indexes/17.json",
                "assets/log_configs/log.xml",
                "versions/1.21.8/1.21.8.jar",
            ),
            plan.files.map { it.relativePath },
        )
        val client = plan.files.first { it.relativePath.endsWith(".jar") }
        assertEquals("client-sha", client.sha1)
        assertEquals(100, client.size)
    }

    @Test
    fun `没有规则的库会下载`() {
        val plan = GameFilePlanner.plan(detail(library("com.example:always:1.0")), OperatingSystem.LINUX)

        assertTrue(plan.files.any { it.relativePath.startsWith("libraries/") })
        assertTrue(plan.warnings.isEmpty())
    }

    @Test
    fun `只允许 macOS 的库在 Windows 上被跳过`() {
        val macOnly =
            library(
                "org.lwjgl:lwjgl:3.3.3",
                rules = listOf(LibraryRule(action = "allow", os = OsRule(name = "osx"))),
            )

        val onMac = GameFilePlanner.plan(detail(macOnly), OperatingSystem.MACOS)
        val onWindows = GameFilePlanner.plan(detail(macOnly), OperatingSystem.WINDOWS)

        assertTrue(onMac.files.any { it.relativePath.contains("lwjgl") })
        assertFalse(onWindows.files.any { it.relativePath.contains("lwjgl") })
    }

    @Test
    fun `没有 artifact 的 natives 只记警告不下载`() {
        val nativesOnly =
            Library(
                name = "com.example:natives:1.0",
                downloads = LibraryDownloads(classifiers = mapOf("natives-macos" to DownloadEntry(url = "https://example.invalid/native.jar"))),
            )

        val plan = GameFilePlanner.plan(detail(nativesOnly), OperatingSystem.MACOS)

        assertTrue(plan.files.none { it.relativePath.contains("natives") })
        assertEquals(1, plan.warnings.size)
        assertTrue(plan.warnings.single().contains("natives"))
    }

    @Test
    fun `artifact 缺少 path 时按 Maven 坐标推导`() {
        val lib = library("com.mojang:brigadier:1.3.10", path = null)
        val plan = GameFilePlanner.plan(detail(lib), OperatingSystem.MACOS)

        assertTrue(
            plan.files.any { it.relativePath == "libraries/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar" },
        )
    }

    @Test
    fun `规则匹配语义与官方一致`() {
        val allowAll = listOf(LibraryRule(action = "allow"))
        val allowMac = listOf(LibraryRule(action = "allow", os = OsRule(name = "osx")))
        val denyMacThenAllowAll =
            listOf(
                LibraryRule(action = "deny", os = OsRule(name = "osx")),
                LibraryRule(action = "allow"),
            )

        assertTrue(GameFilePlanner.allows(emptyList(), OperatingSystem.MACOS))
        assertTrue(GameFilePlanner.allows(allowAll, OperatingSystem.MACOS))
        assertTrue(GameFilePlanner.allows(allowMac, OperatingSystem.MACOS))
        assertFalse(GameFilePlanner.allows(allowMac, OperatingSystem.LINUX))
        assertTrue(GameFilePlanner.allows(denyMacThenAllowAll, OperatingSystem.MACOS))
    }
}
