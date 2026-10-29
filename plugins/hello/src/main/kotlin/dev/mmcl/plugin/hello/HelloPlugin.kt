package dev.mmcl.plugin.hello

import dev.mmcl.api.capability.Account
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.api.event.PluginEnabled
import dev.mmcl.api.event.Subscription
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext

/**
 * 内置示例插件：演示 `META-INF/services` 发现、服务注册与事件订阅。
 *
 * 它不做任何真实工作，只用来验证插件运行时链路是否通畅。
 */
class HelloPlugin : Plugin {
    override val id: String = "dev.mmcl.plugin.hello"
    override val version: String = "0.0.1"

    private var context: PluginContext? = null
    private var subscription: Subscription? = null

    override fun onLoad(context: PluginContext) {
        this.context = context
        context.services.register(AuthProvider::class, HelloAuthProvider())
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

/** 示例认证实现，只为证明服务注册可用。 */
class HelloAuthProvider : AuthProvider {
    override val id: String = "hello"

    override suspend fun login(): Account =
        Account(uuid = "00000000-0000-0000-0000-000000000000", name = "Hello")
}
