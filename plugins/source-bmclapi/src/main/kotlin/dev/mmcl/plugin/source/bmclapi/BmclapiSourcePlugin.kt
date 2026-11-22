package dev.mmcl.plugin.source.bmclapi

import dev.mmcl.api.ApiLevel
import dev.mmcl.api.capability.UrlMirror
import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.plugin.PluginIcon

/** 注册 [BmclapiVersionSource] 的插件入口。 */
class BmclapiSourcePlugin : Plugin {
    override val id: String = "dev.mmcl.source.bmclapi.plugin"
    override val version: String = "0.0.2"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val title: String
        get() = if (isChinese(localeProvider())) "BMCLAPI 国内镜像" else "BMCLAPI mirror"
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.SOURCE

    /** `onLoad` 之前不读语言，先按英文兜底。 */
    private var localeProvider: () -> String = { "en" }

    override fun onLoad(context: PluginContext) {
        localeProvider = { context.locale }
        context.services.register(
            VersionManifestSource::class,
            BmclapiVersionSource(localeProvider = { context.locale }),
        )
        // 给加载器/内容插件用：Fabric 的 Maven 库走 BMCLAPI 镜像（实测字节一致）
        context.services.register(UrlMirror::class, BmclapiUrlMirror())
        context.logger.info("BMCLAPI 版本源已注册（资源来自 BMCLAPI）")
    }
}

/** 界面语言是否为中文；插件展示名与来源标注都按它选。 */
internal fun isChinese(locale: String): Boolean = locale.lowercase().startsWith("zh")
