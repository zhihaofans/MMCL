package dev.mmcl.auth.microsoft

import dev.mmcl.api.settings.SettingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MicrosoftAuthSettingsTest {
    private val section = MicrosoftAuthSettings()

    @Test
    fun `设置项就是 client id 对应的设置键`() {
        val items = section.items("zh-CN")

        assertEquals(1, items.size)
        val text = items.single() as SettingItem.Text
        assertEquals(MicrosoftAuthPlugin.CLIENT_ID_KEY, text.key)
    }

    @Test
    fun `标题与说明跟随界面语言`() {
        assertEquals("微软账号", section.title("zh-CN"))
        assertEquals("Microsoft account", section.title("en"))
        assertTrue(section.description("zh-CN")!!.contains("Azure"))
        assertTrue(section.description("en")!!.contains("Azure"))
    }
}
