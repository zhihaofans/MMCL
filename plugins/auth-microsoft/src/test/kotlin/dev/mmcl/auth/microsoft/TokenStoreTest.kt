package dev.mmcl.auth.microsoft

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class TokenStoreTest {
    private val logger = LoggerFactory.getLogger("test.token-store")

    private fun tempStore(): TokenStore {
        val dir = Files.createTempDirectory("mmcl-auth")
        return TokenStore(dir.resolve("tokens.properties"), logger)
    }

    @Test
    fun `保存后能原样读回`() {
        val store = tempStore()

        store.save(StoredTokens("refresh-1", "mc-1", 1_700_000_000, "uuid-1", "Steve"))

        val loaded = store.load()
        assertEquals("refresh-1", loaded?.refreshToken)
        assertEquals("mc-1", loaded?.accessToken)
        assertEquals(1_700_000_000L, loaded?.accessTokenExpiresAt)
        assertEquals("uuid-1", loaded?.uuid)
        assertEquals("Steve", loaded?.name)
    }

    @Test
    fun `皮肤地址能一起落盘`() {
        val store = tempStore()

        store.save(
            StoredTokens(
                refreshToken = "refresh-1",
                accessToken = "mc-1",
                accessTokenExpiresAt = 1,
                uuid = "uuid-1",
                name = "Steve",
                skinUrl = "https://textures.minecraft.net/texture/abc",
            ),
        )

        assertEquals("https://textures.minecraft.net/texture/abc", store.load()?.skinUrl)
    }

    @Test
    fun `文件权限收紧到仅属主可读写`() {
        val dir = Files.createTempDirectory("mmcl-auth")
        val file = dir.resolve("tokens.properties")
        TokenStore(file, logger).save(StoredTokens("refresh-1", null, 0, null, null))

        val permissions = Files.getPosixFilePermissions(file)

        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
    }

    @Test
    fun `清除后按未登录处理`() {
        val store = tempStore()
        store.save(StoredTokens("refresh-1", null, 0, null, null))

        store.clear()

        assertNull(store.load())
        // 再清一次不应抛异常（onDisable 幂等）
        store.clear()
    }

    @Test
    fun `文件损坏时按未登录处理`() {
        val dir = Files.createTempDirectory("mmcl-auth")
        val file = dir.resolve("tokens.properties")
        Files.writeString(file, "这不是 properties\u0000")

        assertNull(TokenStore(file, logger).load())
    }

    @Test
    fun `没有刷新令牌视为未登录`() {
        val dir = Files.createTempDirectory("mmcl-auth")
        val file = dir.resolve("tokens.properties")
        Files.writeString(file, "access_token=abc\nexpires_at=123\n")

        assertNull(TokenStore(file, logger).load())
    }

    @Test
    fun `读不存在的文件返回 null`() {
        val dir = Files.createTempDirectory("mmcl-auth")

        assertTrue(TokenStore(dir.resolve("missing.properties"), logger).load() == null)
    }
}
