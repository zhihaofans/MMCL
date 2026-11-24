package dev.mmcl.plugin.content.modrinth

import dev.mmcl.api.ApiLevel
import dev.mmcl.api.capability.ContentSource
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.plugin.PluginIcon

/**
 * Modrinth 内容源插件：注册 [ContentSource]，由启动器负责搜索 UI、依赖解析与下载。
 *
 * 需要 `ContentSource` 能力与插件最低启动器版本校验，因此要求 MMCL ≥ 0.0.8。
 */
class ModrinthPlugin : Plugin {
    override val id: String = "dev.mmcl.download.modrinth"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val minLauncherVersion: String = "0.0.8"
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.SOURCE

    override val title: String
        get() = if (localeProvider().lowercase().startsWith("zh")) "Modrinth 内容源" else "Modrinth content source"

    private var localeProvider: () -> String = { "en" }

    override fun onLoad(context: PluginContext) {
        localeProvider = { context.locale }
        val http = JdkModrinthHttp("MMCL-Modrinth/${context.launcher.version} (github.com/zhihaofans/MMCL)")
        val client = ModrinthClient(http, context.logger)
        context.services.register(ContentSource::class, ModrinthContentSource(client))
        context.logger.info("Modrinth 内容源已注册")
    }
}
