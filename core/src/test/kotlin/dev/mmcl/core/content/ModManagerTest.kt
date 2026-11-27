package dev.mmcl.core.content

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class ModManagerTest {
    private val logger = LoggerFactory.getLogger("test.mod-manager")
    private lateinit var instanceDir: Path
    private lateinit var modsDir: Path
    private val manager = ModManager(logger)

    @BeforeTest
    fun setUp() {
        instanceDir = Files.createTempDirectory("mmcl-mod-manager")
        modsDir = Files.createDirectories(instanceDir.resolve("mods"))
    }

    @AfterTest
    fun tearDown() {
        instanceDir.toFile().deleteRecursively()
    }

    private fun modJar(name: String, id: String, version: String = "1.0.0", fileName: String = "$name.jar") {
        val target = modsDir.resolve(fileName)
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("fabric.mod.json"))
            zip.write("""{"id":"$id","name":"$name","version":"$version"}""".toByteArray())
            zip.closeEntry()
        }
    }

    @Test
    fun `扫描启用与禁用并读取 fabric 元数据`() {
        modJar("Sodium", "sodium", "1.2.3")
        modJar("Mod Menu", "modmenu", "2.0.0", fileName = "modmenu.jar.disabled")

        val mods = manager.list(instanceDir)

        val sodium = mods.first { it.id == "sodium" }
        assertEquals("Sodium", sodium.name)
        assertEquals("1.2.3", sodium.version)
        assertTrue(sodium.enabled)
        val modmenu = mods.first { it.id == "modmenu" }
        assertFalse(modmenu.enabled)
    }

    @Test
    fun `同 id 多个文件标记冲突`() {
        modJar("Sodium", "sodium", "1.2.3")
        modJar("Sodium", "sodium", "1.2.4", fileName = "sodium-new.jar")

        val mods = manager.list(instanceDir)

        assertEquals(2, mods.size)
        assertTrue(mods.all { it.conflict })
    }

    @Test
    fun `启停通过重命名实现`() {
        modJar("Sodium", "sodium")

        val disabled = manager.setEnabled(instanceDir, "Sodium.jar", enabled = false).getOrThrow()
        assertEquals("Sodium.jar.disabled", disabled.fileName.toString())
        assertFalse(manager.list(instanceDir).single().enabled)

        val enabled = manager.setEnabled(instanceDir, "Sodium.jar.disabled", enabled = true).getOrThrow()
        assertEquals("Sodium.jar", enabled.fileName.toString())
        assertTrue(manager.list(instanceDir).single().enabled)
    }

    @Test
    fun `删除与路径校验`() {
        modJar("Sodium", "sodium")

        assertTrue(manager.delete(instanceDir, "Sodium.jar").isSuccess)
        assertTrue(manager.list(instanceDir).isEmpty())

        assertTrue(manager.delete(instanceDir, "../escape.jar").isFailure)
        assertTrue(manager.setEnabled(instanceDir, "../escape.jar", enabled = false).isFailure)
    }
}
