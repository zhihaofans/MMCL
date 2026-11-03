package dev.mmcl.app.appearance

import dev.mmcl.core.settings.InMemorySettingsService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppearanceStoreTest {
    private val settings = InMemorySettingsService()
    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("mmcl-appearance")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun fontFile(name: String = "MyFont.ttf"): Path {
        val file = tempDir.resolve(name)
        Files.write(file, byteArrayOf(0x00, 0x01, 0x00, 0x00))
        return file
    }

    @Test
    fun `没设置时用默认字体`() {
        assertNull(AppearanceStore(settings).load().fontFile)
    }

    @Test
    fun `选中字体后能读回`() {
        val font = fontFile()

        AppearanceStore(settings).setFontFile(font)

        assertEquals(font.toAbsolutePath().normalize(), AppearanceStore(settings).fontFile())
    }

    @Test
    fun `文件被删掉后自动退回默认`() {
        val font = fontFile()
        AppearanceStore(settings).setFontFile(font)
        Files.delete(font)

        assertNull(AppearanceStore(settings).fontFile())
    }

    @Test
    fun `非字体扩展名不被采用`() {
        val notFont = tempDir.resolve("readme.txt")
        Files.write(notFont, byteArrayOf(1))

        AppearanceStore(settings).setFontFile(notFont)

        assertNull(AppearanceStore(settings).fontFile())
    }

    @Test
    fun `清空设置恢复默认`() {
        AppearanceStore(settings).setFontFile(fontFile())
        AppearanceStore(settings).setFontFile(null)

        assertNull(AppearanceStore(settings).fontFile())
    }

    @Test
    fun `扩展名大小写都认`() {
        val font = fontFile("Font.OTF")

        AppearanceStore(settings).setFontFile(font)

        assertEquals(font.toAbsolutePath().normalize(), AppearanceStore(settings).fontFile())
    }

    @Test
    fun `背景图只认常见图片扩展名`() {
        val png = tempDir.resolve("wall.png")
        Files.write(png, byteArrayOf(1, 2, 3))
        val txt = tempDir.resolve("wall.txt")
        Files.write(txt, byteArrayOf(1))

        AppearanceStore(settings).setBackgroundImage(png)
        assertEquals(png.toAbsolutePath().normalize(), AppearanceStore(settings).backgroundImage())

        AppearanceStore(settings).setBackgroundImage(txt)
        assertNull(AppearanceStore(settings).backgroundImage())
    }

    @Test
    fun `背景图被删掉后自动退回主题`() {
        val png = tempDir.resolve("wall.png")
        Files.write(png, byteArrayOf(1))
        AppearanceStore(settings).setBackgroundImage(png)
        Files.delete(png)

        assertNull(AppearanceStore(settings).backgroundImage())
    }

    @Test
    fun `坏图片加载返回 null`() {
        val broken = tempDir.resolve("broken.png")
        Files.write(broken, byteArrayOf(1, 2, 3))

        assertNull(loadBackgroundBitmap(broken))
    }

    @Test
    fun `坏掉的字体文件加载时返回 null 而不是抛异常`() {
        val broken = fontFile("broken.ttf")

        assertNull(loadFontFamily(broken))
    }
}
