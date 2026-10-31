package dev.mmcl.plugin.auth.offline

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class OfflineAuthProviderTest {
    @Test
    fun `离线 UUID 与凭据符合官方约定`() = runBlocking {
        val provider = OfflineAuthProvider { "Steve" }

        val session = provider.createSession("Alex")

        assertEquals("Alex", session.name)
        assertEquals("0", session.accessToken)
        assertEquals("legacy", session.userType)
        assertEquals(
            UUID.nameUUIDFromBytes("OfflinePlayer:Alex".toByteArray(Charsets.UTF_8)).toString(),
            session.uuid,
        )
    }

    @Test
    fun `同一名字的 UUID 稳定且空名字回退 Player`() = runBlocking {
        val provider = OfflineAuthProvider { "Steve" }

        assertEquals(provider.createSession("Alex").uuid, provider.createSession("Alex").uuid)
        assertEquals("Player", provider.createSession("   ").name)
    }

    @Test
    fun `login 使用配置的玩家名`() = runBlocking {
        val provider = OfflineAuthProvider { "Notch" }

        assertEquals("Notch", provider.login().name)
    }
}
