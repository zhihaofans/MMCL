package dev.mmcl.core.download

import dev.mmcl.api.Architecture
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
    fun `来源插件的地址改写会应用到全部文件`() {
        val plan =
            GameFilePlanner.plan(detail(library("com.example:lib:1.0")), OperatingSystem.MACOS, Architecture.ARM64) { url ->
                url.replace("example.invalid", "mirror.invalid")
            }

        assertTrue(plan.files.isNotEmpty())
        assertTrue(
            plan.files.all { it.url.startsWith("https://mirror.invalid/") },
            "以下地址没有被改写：${plan.files.filterNot { it.url.startsWith("https://mirror.invalid/") }.map { it.url }}",
        )
        assertTrue(
            plan.files.all { it.fallbackUrl?.startsWith("https://example.invalid/") == true },
            "改写后必须保留原地址作为回退：${plan.files.map { it.fallbackUrl }}",
        )
    }

    @Test
    fun `基础文件包含客户端 资源索引 日志配置`() {
        val plan = GameFilePlanner.plan(detail(), OperatingSystem.MACOS, Architecture.ARM64)

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
        val plan = GameFilePlanner.plan(detail(library("com.example:always:1.0")), OperatingSystem.LINUX, Architecture.ARM64)

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

        val onMac = GameFilePlanner.plan(detail(macOnly), OperatingSystem.MACOS, Architecture.ARM64)
        val onWindows = GameFilePlanner.plan(detail(macOnly), OperatingSystem.WINDOWS, Architecture.ARM64)

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

        val plan = GameFilePlanner.plan(detail(nativesOnly), OperatingSystem.MACOS, Architecture.ARM64)

        assertTrue(plan.files.none { it.relativePath.contains("natives") })
        assertEquals(1, plan.warnings.size)
        assertTrue(plan.warnings.single().contains("natives"))
    }

    @Test
    fun `artifact 缺少 path 时按 Maven 坐标推导`() {
        val lib = library("com.mojang:brigadier:1.3.10", path = null)
        val plan = GameFilePlanner.plan(detail(lib), OperatingSystem.MACOS, Architecture.ARM64)

        assertTrue(
            plan.files.any { it.relativePath == "libraries/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar" },
        )
    }

    @Test
    fun `natives 只保留匹配当前架构的变体`() {
        val arm64 =
            Library(
                name = "org.lwjgl:lwjgl:3.4.3:natives-macos-arm64",
                downloads = LibraryDownloads(artifact = DownloadEntry(url = "u", path = "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-macos-arm64.jar")),
                rules = listOf(LibraryRule(action = "allow", os = OsRule(name = "osx"))),
            )
        val x86 =
            Library(
                name = "org.lwjgl:lwjgl:3.4.3:natives-macos",
                downloads = LibraryDownloads(artifact = DownloadEntry(url = "u", path = "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-macos.jar")),
                rules = listOf(LibraryRule(action = "allow", os = OsRule(name = "osx"))),
            )

        val onArm = GameFilePlanner.allowedLibraries(detail(arm64, x86), OperatingSystem.MACOS, Architecture.ARM64)
        val onIntel = GameFilePlanner.allowedLibraries(detail(arm64, x86), OperatingSystem.MACOS, Architecture.X86_64)

        assertEquals(listOf("org.lwjgl:lwjgl:3.4.3:natives-macos-arm64"), onArm.map { it.name })
        assertEquals(listOf("org.lwjgl:lwjgl:3.4.3:natives-macos"), onIntel.map { it.name })
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
