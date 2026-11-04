package dev.mmcl.plugin.auth.offline

import dev.mmcl.api.settings.SettingItem
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

    @Test
    fun `展示名跟随界面语言`() {
        assertEquals("离线账户", OfflineAuthProvider(localeProvider = { "zh-CN" }).displayName)
        assertEquals("Offline account", OfflineAuthProvider(localeProvider = { "en" }).displayName)
    }

    @Test
    fun `设置区暴露玩家名`() {
        val section = OfflineAuthSettings()
        val items = section.items("zh-CN")

        assertEquals("dev.mmcl.auth.offline", section.id)
        assertEquals("离线账户", section.title("zh-CN"))
        assertEquals("Offline account", section.title("en"))
        val text = items.single() as SettingItem.Text
        assertEquals("player.name", text.key)
        assertEquals("玩家名", text.title)
    }
}
