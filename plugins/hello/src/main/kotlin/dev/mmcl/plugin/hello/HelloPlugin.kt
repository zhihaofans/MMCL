package dev.mmcl.plugin.hello

import dev.mmcl.api.event.PluginEnabled
import dev.mmcl.api.event.Subscription
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.plugin.PluginContext

/**
 * 内置示例插件：演示 `META-INF/services` 发现与事件订阅。
 *
 * 它不做任何真实工作，只用来验证插件运行时链路是否通畅。
 * 这里**不注册能力**：能力会参与真实选择（例如认证），示例插件不该抢真实插件的活。
 */
class HelloPlugin : Plugin {
    override val id: String = "dev.mmcl.plugin.hello"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val title: String
        get() = if (isChinese(localeProvider())) "Hello 示例" else "Hello sample"
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.TOOL

    /** `onLoad` 之前不读语言，先按英文兜底。 */
    private var localeProvider: () -> String = { ENGLISH }

    private var context: PluginContext? = null
    private var subscription: Subscription? = null

    override fun onLoad(context: PluginContext) {
        this.context = context
        localeProvider = { context.locale }
        context.logger.info("Hello 插件已加载，数据目录：{}", context.dataDir)
    }

    override fun onEnable() {
        val current = context ?: return
        subscription =
            current.events.subscribe(PluginEnabled::class) { event ->
                if (event.pluginId != id) {
                    current.logger.info("插件 {} 已启用", event.pluginId)
                }
            }
        current.logger.info("Hello 插件已启用，平台：{} / {}", current.launcher.os, current.launcher.arch)
    }

    override fun onDisable() {
        subscription?.cancel()
        subscription = null
        context?.logger?.info("Hello 插件已禁用")
    }
}

/** 缺省语言：`onLoad` 之前用英文兜底。 */
private const val ENGLISH = "en"

/** 界面语言是否为中文；插件展示名跟随 `PluginContext.locale`，不写死。 */
private fun isChinese(locale: String): Boolean = locale.lowercase().startsWith("zh")
