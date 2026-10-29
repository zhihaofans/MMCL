package dev.mmcl.core.builtin

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class BuiltinPluginSyncTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var resources: Path
    private lateinit var plugins: Path

    @BeforeTest
    fun setUp() {
        resources = Files.createTempDirectory("mmcl-builtin-res")
        plugins = Files.createTempDirectory("mmcl-builtin-target")
        Files.createDirectories(resources.resolve(BuiltinPluginSync.RESOURCE_DIR))
    }

    @AfterTest
    fun tearDown() {
        resources.toFile().deleteRecursively()
        plugins.toFile().deleteRecursively()
    }

    private fun writeJar(name: String, size: Int) {
        Files.write(resources.resolve(BuiltinPluginSync.RESOURCE_DIR).resolve(name), ByteArray(size) { it.toByte() })
    }

    private fun writeIndex(vararg names: String) {
        Files.writeString(resources.resolve(BuiltinPluginSync.RESOURCE_DIR).resolve("index.txt"), names.joinToString("\n"))
    }

    private fun resourceLoader(): URLClassLoader = URLClassLoader(arrayOf(resources.toUri().toURL()), null)

    private fun sync(): Int = resourceLoader().use { BuiltinPluginSync.sync(plugins, logger, it) }

    @Test
    fun `复制内置插件并生成清单`() {
        writeJar("a.jar", 3)
        writeIndex("a.jar")

        assertEquals(1, sync())

        assertTrue(Files.exists(plugins.resolve("a.jar")))
        assertTrue(Files.exists(plugins.resolve(".builtin-plugins.manifest")))
    }

    @Test
    fun `重复同步不会重写相同大小的文件`() {
        writeJar("a.jar", 3)
        writeIndex("a.jar")
        sync()

        assertEquals(0, sync())
    }

    @Test
    fun `新版本替换旧版本并清理旧文件`() {
        writeJar("plugin-1.jar", 3)
        writeIndex("plugin-1.jar")
        sync()

        writeJar("plugin-2.jar", 5)
        writeIndex("plugin-2.jar")
        assertEquals(1, sync())

        assertFalse(Files.exists(plugins.resolve("plugin-1.jar")))
        assertTrue(Files.exists(plugins.resolve("plugin-2.jar")))
    }

    @Test
    fun `用户手动放入的插件不会被清理`() {
        Files.write(plugins.resolve("user-plugin.jar"), byteArrayOf(7))
        writeJar("a.jar", 3)
        writeIndex("a.jar")

        sync()
        writeIndex("b.jar")
        writeJar("b.jar", 4)
        sync()

        assertTrue(Files.exists(plugins.resolve("user-plugin.jar")))
    }

    @Test
    fun `没有索引时跳过同步`() {
        assertEquals(0, sync())
        assertTrue(Files.list(plugins).use { it.count() } == 0L)
    }

    @Test
    fun `拒绝路径穿越的文件名`() {
        writeIndex("../evil.jar")

        assertEquals(0, sync())

        assertFalse(Files.exists(plugins.parent.resolve("evil.jar")))
    }
}
