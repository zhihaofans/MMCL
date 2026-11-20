package dev.mmcl.core.plugin

import dev.mmcl.api.plugin.Plugin
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

/** 供 ServiceLoader 实例化的测试插件（高版本）。 */
class JarPluginV2 : Plugin {
    override val id: String = "dev.mmcl.test.jar"
    override val version: String = "2.0.0"
}

/** 供 ServiceLoader 实例化的测试插件（低版本）。 */
class JarPluginV1 : Plugin {
    override val id: String = "dev.mmcl.test.jar"
    override val version: String = "1.0.0"
}

/** 独立 id 的测试插件。 */
class StandaloneJarPlugin : Plugin {
    override val id: String = "dev.mmcl.test.standalone"
    override val version: String = "0.1.0"
    override val dependencies: List<String> = listOf("dev.mmcl.test.jar")
}

class PluginLoaderTest {
    private val logger = LoggerFactory.getLogger("test")
    private val loader = PluginLoader(logger)

    private fun tempDir(): Path = Files.createTempDirectory("mmcl-loader-test")

    private fun writeJar(
        dir: Path,
        fileName: String,
        serviceDeclaration: String,
        extra: String? = null,
    ): Path {
        val jar = dir.resolve(fileName)
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            out.putNextEntry(JarEntry("META-INF/services/${Plugin::class.java.name}"))
            out.write(serviceDeclaration.toByteArray(Charsets.UTF_8))
            out.closeEntry()
            if (extra != null) {
                out.putNextEntry(JarEntry("extra.txt"))
                out.write(extra.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
        return jar
    }

    @Test
    fun `从 JAR 的 services 声明发现插件`() {
        val dir = tempDir()
        writeJar(dir, "standalone.jar", StandaloneJarPlugin::class.java.name)

        val discovered = loader.discover(dir)

        assertEquals(1, discovered.size)
        val descriptor = discovered.single()
        assertEquals("dev.mmcl.test.standalone", descriptor.id)
        assertEquals("0.1.0", descriptor.version)
        assertEquals(listOf("dev.mmcl.test.jar"), descriptor.dependencies)
        assertEquals(dir.resolve("standalone.jar"), descriptor.jar)
        assertTrue(descriptor.plugin is StandaloneJarPlugin)
        descriptor.classLoader?.close()
    }

    @Test
    fun `同一 id 多版本时保留较高版本`() {
        val dir = tempDir()
        writeJar(dir, "v1.jar", JarPluginV1::class.java.name)
        writeJar(dir, "v2.jar", JarPluginV2::class.java.name)

        val discovered = loader.discover(dir)

        assertEquals(1, discovered.size)
        assertEquals("2.0.0", discovered.single().version)
        assertTrue(discovered.single().plugin is JarPluginV2)
        discovered.forEach { it.classLoader?.close() }
    }

    @Test
    fun `同版本不同内容保留先发现的并记录冲突`() {
        val dir = tempDir()
        writeJar(dir, "a.jar", JarPluginV1::class.java.name, extra = "a")
        writeJar(dir, "b.jar", JarPluginV1::class.java.name, extra = "b")

        val discovered = loader.discover(dir)

        assertEquals(1, discovered.size)
        assertEquals("1.0.0", discovered.single().version)
        assertEquals(1, loader.conflicts.size)
        val conflict = loader.conflicts.single()
        assertEquals(PluginConflictReason.DUPLICATE, conflict.reason)
        assertEquals("dev.mmcl.test.jar", conflict.id)
        // 冲突里记的是被忽略的 b.jar；保留的是先发现的 a.jar
        assertEquals("b.jar", conflict.jar.fileName.toString())
        assertEquals("同版本不同内容", conflict.detail)
        assertEquals(
            PluginIntegrityVerifier.sha256Of(dir.resolve("a.jar")),
            discovered.single().integrity?.sha256,
        )
        discovered.forEach { it.classLoader?.close() }
    }

    @Test
    fun `没有 services 声明的 JAR 被忽略`() {
        val dir = tempDir()
        JarOutputStream(Files.newOutputStream(dir.resolve("plain.jar"))).use { out ->
            out.putNextEntry(JarEntry("some/Other.class"))
            out.write(byteArrayOf(0))
            out.closeEntry()
        }

        assertTrue(loader.discover(dir).isEmpty())
    }

    @Test
    fun `损坏的 services 声明被隔离`() {
        val dir = tempDir()
        writeJar(dir, "broken.jar", "dev.mmcl.test.DoesNotExist")
        writeJar(dir, "good.jar", StandaloneJarPlugin::class.java.name)

        val discovered = loader.discover(dir)

        assertEquals(listOf("dev.mmcl.test.standalone"), discovered.map { it.id })
        discovered.forEach { it.classLoader?.close() }
    }

    @Test
    fun `目录不存在时返回空列表`() {
        assertTrue(loader.discover(Path.of("build", "tmp", "definitely-missing-dir")).isEmpty())
    }
}
