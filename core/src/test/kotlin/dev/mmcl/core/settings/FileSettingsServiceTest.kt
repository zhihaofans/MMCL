package dev.mmcl.core.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSettingsServiceTest {
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("mmcl-settings")
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun file() = dir.resolve("settings.properties")

    @Test
    fun `未写入时返回默认值`() {
        val service = FileSettingsService(file())

        assertEquals("fallback", service.getString("ui.theme", "fallback"))
        assertNull(service.getString("ui.theme"))
    }

    @Test
    fun `写入后重建实例仍能读到`() {
        val path = file()
        FileSettingsService(path).putString("ui.theme", "builtin.light")

        assertEquals("builtin.light", FileSettingsService(path).getString("ui.theme"))
        assertTrue(Files.exists(path))
    }

    @Test
    fun `写入 null 会删除键`() {
        val path = file()
        val service = FileSettingsService(path)
        service.putString("ui.theme", "builtin.light")
        service.putString("ui.theme", null)

        assertNull(FileSettingsService(path).getString("ui.theme"))
    }

    @Test
    fun `父目录不存在时自动创建`() {
        val path = dir.resolve("nested").resolve("settings.properties")

        FileSettingsService(path).putString("key", "value")

        assertEquals("value", FileSettingsService(path).getString("key"))
    }

    @Test
    fun `文件内容按键排序且可忽略注释`() {
        val path = file()
        val service = FileSettingsService(path)
        service.putString("b.key", "2")
        service.putString("a.key", "1")

        val lines = Files.readAllLines(path)
        assertEquals(listOf("a.key=1", "b.key=2"), lines)
    }
}
