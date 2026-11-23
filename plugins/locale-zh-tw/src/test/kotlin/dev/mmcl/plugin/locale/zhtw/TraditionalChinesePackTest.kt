package dev.mmcl.plugin.locale.zhtw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraditionalChinesePackTest {
    private val pack = TraditionalChinesePack()

    @Test
    fun `語言標籤與顯示名`() {
        assertEquals("zh-TW", pack.locale)
        assertEquals("繁體中文", pack.displayName)
    }

    @Test
    fun `載入全部文案`() {
        val strings = pack.strings()

        assertTrue(strings.size >= 50, "只載入 ${strings.size} 條，資源檔可能沒打進 JAR")
        assertTrue(strings.values.none { it.isBlank() }, "有空字串")
    }

    @Test
    fun `設定頁關鍵文案已翻譯`() {
        val strings = pack.strings()

        assertEquals("設定", strings["settings.title"])
        assertEquals("首頁", strings["page.home.title"])
        assertEquals("外掛", strings["page.plugins.title"])
        assertEquals("資料目錄", strings["settings.about.data_dir"])
        assertEquals("跟隨系統", strings["settings.language.auto"])
    }

    @Test
    fun `格式化佔位符保留下來`() {
        val strings = pack.strings()

        assertTrue(strings.getValue("shell.status.count").contains("%1\$d"))
        assertTrue(strings.getValue("shell.status.count").contains("%2\$d"))
        assertTrue(strings.getValue("settings.priority").contains("%2\$d"))
    }

    @Test
    fun `外掛入口會註冊語言包`() {
        val plugin = TraditionalChineseLocalePlugin()

        assertEquals("dev.mmcl.locale.zh_tw", plugin.id)
        assertEquals("0.0.2", plugin.version)
    }

    @Test
    fun `覆盖全部内建文案`() {
        val strings = pack.strings()

        assertTrue(strings.size >= 280)
        assertEquals("模組", strings["instances.folder.mods"])
        assertEquals("建立執行個體", strings["instances.create"])
    }
}
