package dev.mmcl.core.plugin

import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.event.EventBus
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.service.ServiceRegistry
import java.nio.file.Path
import org.slf4j.Logger

/**
 * [PluginContext] 的唯一实现，只由 [PluginManager] 构造。
 *
 * 每个插件拿到独立的 `dataDir` 与带前缀的 logger，避免插件之间互相踩数据。
 */
internal class PluginContextImpl(
    override val pluginId: String,
    override val dataDir: Path,
    override val logger: Logger,
    override val events: EventBus,
    override val services: ServiceRegistry,
    override val launcher: LauncherInfo,
) : PluginContext
