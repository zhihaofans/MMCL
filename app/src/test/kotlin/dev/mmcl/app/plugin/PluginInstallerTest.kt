package dev.mmcl.app.plugin

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class PluginInstallerTest {
    private val logger = LoggerFactory.getLogger("test.plugin-installer")
    private lateinit var root: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("mmcl-plugin-installer")
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private fun installer() = PluginInstaller(root.resolve("plugins"), logger)

    @Test
    fun `复制 JAR 到插件目录`() {
        val source = root.resolve("demo-1.0.jar")
        Files.writeString(source, "jar-bytes")

        val target = installer().install(source).getOrThrow()

        assertEquals(root.resolve("plugins/demo-1.0.jar"), target)
        assertEquals("jar-bytes", Files.readString(target))
        assertTrue(Files.isRegularFile(source))
    }

    @Test
    fun `同名文件会被替换`() {
        val source = root.resolve("demo-1.0.jar")
        Files.writeString(source, "new")
        val plugins = root.resolve("plugins")
        Files.createDirectories(plugins)
        Files.writeString(plugins.resolve("demo-1.0.jar"), "old")

        installer().install(source).getOrThrow()

        assertEquals("new", Files.readString(plugins.resolve("demo-1.0.jar")))
    }

    @Test
    fun `拒绝非 JAR`() {
        val source = root.resolve("notes.txt")
        Files.writeString(source, "text")

        assertFailsWith<Throwable> { installer().install(source).getOrThrow() }
    }
}
