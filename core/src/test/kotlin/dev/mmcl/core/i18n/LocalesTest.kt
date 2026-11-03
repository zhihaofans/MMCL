package dev.mmcl.core.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalesTest {
    @Test
    fun `缺省跟随系统，中文系统用简体中文`() {
        assertEquals(Locales.CHINESE, Locales.resolve(null, "zh-Hans-CN"))
        assertEquals(Locales.CHINESE, Locales.resolve("", "zh-TW"))
    }

    @Test
    fun `非中文系统用英文`() {
        assertEquals(Locales.ENGLISH, Locales.resolve(null, "ja-JP"))
        assertEquals(Locales.ENGLISH, Locales.resolve(null, "en-US"))
    }

    @Test
    fun `显式语言优先于系统`() {
        assertEquals(Locales.ENGLISH, Locales.resolve(Locales.ENGLISH, "zh-CN"))
        assertEquals(Locales.CHINESE, Locales.resolve(Locales.CHINESE, "en-US"))
        assertEquals("ja-JP", Locales.resolve("ja-JP", "zh-CN"))
    }

    @Test
    fun `auto 重新解析系统语言`() {
        assertEquals(Locales.CHINESE, Locales.resolve(Locales.AUTO, "zh-Hans"))
    }
}
