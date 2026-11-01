package dev.mmcl.core.java

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.JavaRuntimeProvider
import dev.mmcl.api.capability.RuntimeDownload
import dev.mmcl.core.net.HttpTransport
import dev.mmcl.core.settings.InMemorySettingsService
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JavaRuntimeSourcesTest {
    private class FakeProvider(
        override val id: String,
        override val priority: Int = 100,
        override val displayName: String = id,
        private val download: RuntimeDownload? = null,
        private val failWith: String? = null,
    ) : JavaRuntimeProvider {
        override suspend fun resolve(
            majorVersion: Int,
            os: OperatingSystem,
            arch: Architecture,
        ): RuntimeDownload? {
            failWith?.let { error(it) }
            return download
        }
    }

    private val settings = InMemorySettingsService()

    private fun sources(vararg providers: JavaRuntimeProvider) =
        JavaRuntimeSources(providers = { providers.toList() }, settings = settings)

    @Test
    fun `默认按优先级再按 id 排序`() {
        val sources = sources(FakeProvider("b", 200), FakeProvider("a", 100), FakeProvider("c", 100))

        assertEquals(listOf("a", "c", "b"), sources.all().map { it.id })
    }

    @Test
    fun `选中的来源排最前，其余顺序不变`() {
        val sources = sources(FakeProvider("a", 100), FakeProvider("b", 200), FakeProvider("c", 300))
        sources.select("c")

        assertEquals(listOf("c", "a", "b"), sources.ordered().map { it.id })
        assertEquals("c", sources.current()?.id)
    }

    @Test
    fun `选中的来源不存在时退回按优先级`() {
        val sources = sources(FakeProvider("a", 100), FakeProvider("b", 200))
        settings.putString(JavaRuntimeSources.SETTING_KEY, "gone")

        assertEquals(listOf("a", "b"), sources.ordered().map { it.id })
        assertNull(sources.selectedId())
    }

    @Test
    fun `auto 等同未选`() {
        val sources = sources(FakeProvider("a", 100), FakeProvider("b", 200))
        settings.putString(JavaRuntimeSources.SETTING_KEY, JavaRuntimeSources.AUTO)

        assertNull(sources.selectedId())
        assertEquals("a", sources.current()?.id)
    }

    @Test
    fun `按顺序问每个来源，返回第一个能给出的`() = runTest {
        val sources =
            sources(
                FakeProvider("a", 100),
                FakeProvider("b", 200, download = RuntimeDownload(url = "https://example.invalid/b.zip")),
            )

        val resolved = sources.resolve(21, OperatingSystem.LINUX, Architecture.X86_64)

        assertEquals("b", resolved?.first?.id)
        assertEquals("https://example.invalid/b.zip", resolved?.second?.url)
    }

    @Test
    fun `某个来源抛异常时换下一个`() = runTest {
        val sources =
            sources(
                FakeProvider("broken", 100, failWith = "镜像挂了"),
                FakeProvider("ok", 200, download = RuntimeDownload(url = "https://example.invalid/ok.zip")),
            )

        val resolved = sources.resolve(21, OperatingSystem.LINUX, Architecture.X86_64)

        assertEquals("ok", resolved?.first?.id)
    }

    @Test
    fun `所有来源都不支持时返回 null`() = runTest {
        val sources = sources(FakeProvider("a", 100), FakeProvider("b", 200))

        assertNull(sources.resolve(21, OperatingSystem.MACOS, Architecture.ARM64))
    }

    @Test
    fun `第一个来源安装失败时换下一个`() = runTest {
        val root = Files.createTempDirectory("mmcl-sources-install")
        val installer =
            JavaRuntimeInstaller(
                transport = MapTransport(mapOf("https://bad.invalid/jdk.zip" to "not-a-zip".toByteArray(), "https://good.invalid/jdk.zip" to zipFixture())),
                logger = LoggerFactory.getLogger("test"),
                runtimeRoot = root.resolve("runtime"),
                os = OperatingSystem.MACOS,
                arch = Architecture.ARM64,
            )
        val bad = FakeProvider("bad", 100, download = RuntimeDownload(url = "https://bad.invalid/jdk.zip"))
        val good = FakeProvider("good", 200, download = RuntimeDownload(url = "https://good.invalid/jdk.zip"))

        val installed = sources(bad, good).installFirst(25, OperatingSystem.MACOS, Architecture.ARM64, installer)

        assertEquals("good", installed?.first?.id)
        assertTrue(Files.isRegularFile(installed!!.second.resolve("release")))
    }

    @Test
    fun `所有来源安装都失败时抛 IOException`() = runTest {
        val root = Files.createTempDirectory("mmcl-sources-install-fail")
        val installer =
            JavaRuntimeInstaller(
                transport = MapTransport(mapOf("https://bad.invalid/jdk.zip" to "not-a-zip".toByteArray())),
                logger = LoggerFactory.getLogger("test"),
                runtimeRoot = root.resolve("runtime"),
                os = OperatingSystem.MACOS,
                arch = Architecture.ARM64,
            )
        val bad = FakeProvider("bad", 100, download = RuntimeDownload(url = "https://bad.invalid/jdk.zip"))

        assertFailsWith<IOException> {
            sources(bad).installFirst(25, OperatingSystem.MACOS, Architecture.ARM64, installer)
        }
    }

    @Test
    fun `没有来源插件时 current 为空`() {
        val sources = sources()

        assertNull(sources.current())
        assertEquals(false, sources.hasProvider())
    }

    private fun zipFixture(): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            add("jdk-25/release", "JAVA_VERSION=\"25.0.1\"")
            add("jdk-25/bin/java", "#!/bin/sh\n")
        }
        return buffer.toByteArray()
    }

    private class MapTransport(private val files: Map<String, ByteArray>) : HttpTransport {
        override suspend fun getText(url: String): String = ""

        override suspend fun downloadTo(url: String, target: Path) {
            val bytes = files[url] ?: error("unexpected url: $url")
            Files.write(target, bytes)
        }
    }
}
