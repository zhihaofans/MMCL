package dev.mmcl.app.appearance

import dev.mmcl.core.net.HttpTransport
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class SkinLoaderTest {
    private val logger = LoggerFactory.getLogger("test.skin-loader")

    private fun pngBytes(size: Int = 64): ByteArray {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream()
        ImageIO.write(image, "png", bytes)
        return bytes.toByteArray()
    }

    private class FakeTransport(private val body: ByteArray) : HttpTransport {
        var downloads = 0
            private set

        override suspend fun getText(url: String): String = error("皮肤加载不需要文本")

        override suspend fun downloadTo(url: String, target: Path) {
            downloads++
            target.parent?.let { Files.createDirectories(it) }
            Files.write(target, body)
        }
    }

    @Test
    fun `下载一次后新实例走磁盘缓存`() = runBlocking {
        val dir = Files.createTempDirectory("mmcl-skins")
        val transport = FakeTransport(pngBytes())
        val url = "https://textures.minecraft.net/texture/abc"

        val first = SkinLoader(transport, dir, logger).load(url)

        assertNotNull(first)
        assertEquals(64, first.width)
        assertEquals(1, transport.downloads)
        assertEquals(1, Files.list(dir).use { stream -> stream.filter { it.toString().endsWith(".png") }.count() })

        // 新实例没有内存缓存，只能从磁盘读
        val second = SkinLoader(transport, dir, logger).load(url)

        assertNotNull(second)
        assertEquals(1, transport.downloads)
    }

    @Test
    fun `并发加载同一皮肤只下载一次`() = runBlocking {
        val dir = Files.createTempDirectory("mmcl-skins")
        val transport = FakeTransport(pngBytes())
        val loader = SkinLoader(transport, dir, logger)
        val url = "https://textures.minecraft.net/texture/abc"

        val results = (1..4).map { async { loader.load(url) } }.awaitAll()

        assertTrue(results.all { it != null })
        assertEquals(1, transport.downloads)
    }

    @Test
    fun `空地址直接返回 null`() = runBlocking {
        val loader = SkinLoader(FakeTransport(pngBytes()), Files.createTempDirectory("mmcl-skins"), logger)

        assertNull(loader.load(null))
        assertNull(loader.load("   "))
    }

    @Test
    fun `下载失败不抛异常`() = runBlocking {
        val failing =
            object : HttpTransport {
                override suspend fun getText(url: String): String = error("unused")

                override suspend fun downloadTo(url: String, target: Path) {
                    throw java.io.IOException("boom")
                }
            }

        assertNull(SkinLoader(failing, Files.createTempDirectory("mmcl-skins"), logger).load("https://x/y.png"))
        assertNull(SkinLoader(failing, Files.createTempDirectory("mmcl-skins"), logger).load("https://x/y.png"))
    }
}
