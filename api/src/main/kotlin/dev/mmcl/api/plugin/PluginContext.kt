package dev.mmcl.api.plugin

import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.event.EventBus
import dev.mmcl.api.service.ServiceRegistry
import dev.mmcl.api.service.SettingsService
import java.nio.file.Path
import org.slf4j.Logger

/**
 * 插件运行上下文，由 `core` 为每个插件单独构造。
 *
 * 插件只能通过这里暴露的能力访问启动器：文件、事件、服务注册与只读的启动器信息。
 */
interface PluginContext {
    /** 当前插件 id，与 [Plugin.id] 一致。 */
    val pluginId: String

    /** 该插件专属数据目录 `<dataDir>/plugin-data/<pluginId>/`，由 core 创建。 */
    val dataDir: Path

    /** 带插件 id 前缀的日志器，禁止使用 `println`。 */
    val logger: Logger

    /** 事件总线：只用于“通知已发生的事”。 */
    val events: EventBus

    /** 服务注册表：用于“注册可被消费的能力”。 */
    val services: ServiceRegistry

    /** 启动器级键值设置，跨启动持久化；插件自己的配置建议用 JSON 存在 [dataDir] 里。 */
    val settings: SettingsService

    /** 只读的启动器信息（名称、版本、平台、数据目录）。 */
    val launcher: LauncherInfo
}
