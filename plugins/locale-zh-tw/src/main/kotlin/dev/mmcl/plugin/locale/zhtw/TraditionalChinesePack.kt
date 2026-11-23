package dev.mmcl.plugin.locale.zhtw

import dev.mmcl.api.ApiLevel
import dev.mmcl.api.capability.LocalePack
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.plugin.PluginContext
import java.util.Properties

/**
 * 繁體中文語言包（台灣用語），演示 `LocalePack` 擴充點。
 *
 * 涵蓋啟動器目前全部內建 key（由 `verifyLocaleKeys` 保證）；未來新增 key 若還沒翻譯，
 * 仍由 `app` 的回退鏈接手機：`zh-TW → 內建 zh-CN → 內建 en`。
 * 翻譯放在 `resources/i18n/strings_zh_TW.properties`，方便非開發者直接改文字。
 */
class TraditionalChinesePack : LocalePack {
    override val locale: String = "zh-TW"
    override val displayName: String = "繁體中文"

    override fun strings(): Map<String, String> = STRINGS

    private companion object {
        val STRINGS: Map<String, String> by lazy { load() }

        fun load(): Map<String, String> =
            runCatching {
                val properties = Properties()
                TraditionalChinesePack::class.java
                    .getResourceAsStream("/i18n/strings_zh_TW.properties")
                    ?.use { stream -> properties.load(stream.reader(Charsets.UTF_8)) }
                properties.stringPropertyNames().associateWith { key -> properties.getProperty(key) }
            }.getOrDefault(emptyMap())
    }
}

/** 註冊語言包的插件入口。 */
class TraditionalChineseLocalePlugin : Plugin {
    override val id: String = "dev.mmcl.locale.zh_tw"
    override val version: String = "0.0.2"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val title: String = "繁體中文"
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.LOCALE

    override fun onLoad(context: PluginContext) {
        context.services.register(LocalePack::class, TraditionalChinesePack())
        context.logger.info("繁體中文語言包已註冊（zh-TW，{} 條）", TraditionalChinesePack().strings().size)
    }
}
