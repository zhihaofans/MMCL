package dev.mmcl.app.loader

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class LoaderInstallStoreTest {
    private val logger = LoggerFactory.getLogger("test.loader-store")

    @Test
    fun `写入版本 JSON`() {
        val dir = Files.createTempDirectory("mmcl-loader-store")
        val store = LoaderInstallStore(dir, logger)

        val file = store.save("fabric-loader-0.20.1-26.3", """{"id":"fabric-loader-0.20.1-26.3"}""")

        assertTrue(Files.isRegularFile(file))
        assertEquals("""{"id":"fabric-loader-0.20.1-26.3"}""", Files.readString(file))
    }

    @Test
    fun `拒绝路径穿越与空 JSON`() {
        val store = LoaderInstallStore(Files.createTempDirectory("mmcl-loader-store"), logger)

        assertFailsWith<IOException> { store.save("../evil", "{}") }
        assertFailsWith<IOException> { store.save("ok", "  ") }
    }
}
