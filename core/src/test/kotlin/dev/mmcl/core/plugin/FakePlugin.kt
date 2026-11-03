package dev.mmcl.core.plugin

import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext

/** 测试用插件：记录生命周期调用次数，并允许注入会抛异常的回调。 */
class FakePlugin(
    override val id: String,
    override val version: String = "1.0.0",
    override val dependencies: List<String> = emptyList(),
    override val optionalDependencies: List<String> = emptyList(),
    override val apiLevel: Int = 0,
    private val onLoadHook: (PluginContext) -> Unit = {},
    private val onEnableHook: () -> Unit = {},
    private val onDisableHook: () -> Unit = {},
) : Plugin {
    var loadCount: Int = 0
        private set
    var enableCount: Int = 0
        private set
    var disableCount: Int = 0
        private set
    var context: PluginContext? = null
        private set

    override fun onLoad(context: PluginContext) {
        loadCount++
        this.context = context
        onLoadHook(context)
    }

    override fun onEnable() {
        enableCount++
        onEnableHook()
    }

    override fun onDisable() {
        disableCount++
        onDisableHook()
    }
}
