package dev.mmcl.app.i18n

import dev.mmcl.api.capability.LocalePack
import dev.mmcl.core.settings.InMemorySettingsService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleControllerTest {
    private val settings = InMemorySettingsService()

    private fun controller(
        system: String = "en-US",
        vararg packs: LocalePack,
    ) = LocaleController(
        settings = settings,
        packs = { packs.toList() },
        systemLanguageTag = { system },
    )

    private class FakePack(
        override val locale: String,
        override val displayName: String,
        private val strings: Map<String, String>,
    ) : LocalePack {
        override fun strings(): Map<String, String> = strings
    }

    @Test
    fun `缺省跟随系统，中文系统用简体中文`() {
        assertEquals(LocaleController.CHINESE, controller(system = "zh-Hans-CN").currentLocale())
    }

    @Test
    fun `非中文系统用英文`() {
        assertEquals(LocaleController.ENGLISH, controller(system = "ja-JP").currentLocale())
        assertEquals(LocaleController.ENGLISH, controller(system = "en-US").currentLocale())
    }

    @Test
    fun `显式选择优先于系统`() {
        val controller = controller(system = "zh-CN")
        controller.select(LocaleController.ENGLISH)

        assertEquals(LocaleController.ENGLISH, controller.currentLocale())
    }

    @Test
    fun `中文界面能取到中文文案，缺的 key 回退英文`() {
        val strings = controller(system = "zh-CN").strings()

        assertEquals("设置", strings.t("settings.title"))
        assertEquals("设置", strings.t("page.settings.title"))
    }

    @Test
    fun `英文界面取英文文案`() {
        val strings = controller(system = "en-US").strings()

        assertEquals("Settings", strings.t("settings.title"))
    }

    @Test
    fun `缺失的 key 返回 key 本身并告警一次`() {
        val missing = mutableListOf<String>()
        val strings = controller(system = "en-US").strings { missing += it }

        assertEquals("no.such.key", strings.t("no.such.key"))
        assertEquals(listOf("no.such.key"), missing)
    }

    @Test
    fun `插件语言包覆盖内置文案`() {
        val pack =
            FakePack(
                locale = "zh-CN",
                displayName = "简体中文（自定义）",
                strings = mapOf("settings.title" to "设定"),
            )

        assertEquals("设定", controller(system = "zh-CN", pack).strings().t("settings.title"))
    }

    @Test
    fun `插件语言出现在选项里，且不与内置重复`() {
        val options =
            controller(
                system = "en-US",
                FakePack("ja-JP", "日本語", emptyMap()),
                FakePack("zh-CN", "简体中文", emptyMap()),
            ).options()

        assertTrue(options.any { it.locale == "ja-JP" }, options.toString())
        assertEquals(1, options.count { it.locale == "zh-CN" }, "内置 zh-CN 不应重复列出")
    }

    @Test
    fun `占位符按 String format 填充`() {
        val strings = controller(system = "zh-CN").strings()

        assertEquals("插件 3 · 页面 5", strings.t("shell.status.count", 3, 5))
        assertEquals("Settings", controller(system = "en-US").strings().t("settings.title"))
    }

    @Test
    fun `选择写进设置，auto 恢复跟随系统`() {
        val controller = controller(system = "ja-JP")
        controller.select(LocaleController.CHINESE)
        assertEquals(LocaleController.CHINESE, controller.currentLocale())

        controller.select(LocaleController.AUTO)
        assertEquals(LocaleController.ENGLISH, controller.currentLocale())
    }
}
