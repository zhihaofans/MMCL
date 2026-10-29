package dev.mmcl.core.plugin

/** 已发现的插件及其当前状态，供 UI 展示与生命周期管理使用。 */
data class LoadedPlugin(
    val descriptor: PluginDescriptor,
    val state: PluginState,
    val error: Throwable? = null,
) {
    /** 插件 id 的快捷访问。 */
    val id: String get() = descriptor.id

    /** 插件版本的快捷访问。 */
    val version: String get() = descriptor.version
}
