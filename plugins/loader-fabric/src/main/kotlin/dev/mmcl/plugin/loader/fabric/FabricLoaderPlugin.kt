package dev.mmcl.plugin.loader.fabric

import dev.mmcl.api.ApiLevel
import dev.mmcl.api.capability.LoaderInstaller
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.plugin.PluginIcon

/**
 * Fabric 加载器插件：注册 [LoaderInstaller]，由启动器负责安装编排。
 *
 * 只做 Fabric 的元数据、版本选择与 profile 生成，不写文件、不下载游戏文件。
 */
class FabricLoaderPlugin : Plugin {
    override val id: String = "dev.mmcl.loader.fabric"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.TOOL

    override val title: String
        get() = if (localeProvider().lowercase().startsWith("zh")) "Fabric 加载器" else "Fabric loader"

    private var localeProvider: () -> String = { "en" }

    override fun onLoad(context: PluginContext) {
        localeProvider = { context.locale }
        val client = FabricMetaClient(JdkFabricHttp(), context.logger)
        context.services.register(LoaderInstaller::class, FabricInstaller(client, context.logger))
        context.logger.info("Fabric 加载器插件已加载（官方 meta 优先，BMCLAPI 回退）")
    }
}
