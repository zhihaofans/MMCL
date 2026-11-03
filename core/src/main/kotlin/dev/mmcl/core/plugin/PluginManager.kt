package dev.mmcl.core.plugin

import dev.mmcl.api.ApiLevel
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.event.EventBus
import dev.mmcl.api.event.PluginDisabled
import dev.mmcl.api.event.PluginEnabled
import dev.mmcl.api.event.PluginFailed
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.service.ServiceRegistry
import dev.mmcl.api.service.SettingsService
import dev.mmcl.core.event.DefaultEventBus
import dev.mmcl.core.service.DefaultServiceRegistry
import dev.mmcl.core.settings.InMemorySettingsService
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 插件运行时：发现 → 依赖排序 → `onLoad` → `onEnable` → … → `onDisable`。
 *
 * 错误隔离是硬约束（`ARCHITECTURE.md` §8.1）：任何插件回调抛出的 [Throwable]
 * 都会被捕获、记录并反映到 [LoadedPlugin.state]，不会向上传播到主流程。
 *
 * @param launcher 只读启动器信息，用于计算插件数据目录
 * @param logger 运行时日志
 * @param events 事件总线，默认自建
 * @param services 服务注册表，默认自建
 * @param settings 键值设置，默认内存实现（app 会传入持久化实现）
 * @param sorter 依赖排序器
 * @param loader 插件发现器
 */
class PluginManager(
    private val launcher: LauncherInfo,
    private val logger: Logger,
    val events: EventBus = DefaultEventBus(logger),
    val services: ServiceRegistry = DefaultServiceRegistry(),
    val settings: SettingsService = InMemorySettingsService(),
    private val sorter: DependencySorter = DependencySorter(),
    private val loader: PluginLoader = PluginLoader(logger),
) : AutoCloseable {
    private val loaded = LinkedHashMap<String, LoadedPlugin>()
    private var closed = false

    /** 当前已知插件的快照，顺序为加载顺序。 */
    val plugins: List<LoadedPlugin>
        get() = loaded.values.toList()

    /**
     * 扫描目录并启动其中的全部插件。
     *
     * @return 启动后的插件快照
     */
    fun load(pluginsDir: Path): List<LoadedPlugin> {
        check(!closed) { "PluginManager 已关闭" }
        start(loader.discover(pluginsDir))
        return plugins
    }

    /**
     * 启动给定插件（已发现但尚未执行生命周期回调）。测试可直接注入构造好的描述符。
     */
    fun start(descriptors: List<PluginDescriptor>) {
        check(!closed) { "PluginManager 已关闭" }
        val result = sorter.sort(descriptors)
        val byId = descriptors.associateBy { it.id }

        for ((id, missingDeps) in result.missing) {
            val descriptor = byId.getValue(id)
            loaded[id] = LoadedPlugin(descriptor, PluginState.MISSING_DEP)
            logger.warn("插件 {} 缺少依赖 {}，已跳过", id, missingDeps)
        }
        for (id in result.cyclic) {
            val descriptor = byId.getValue(id)
            loaded[id] = LoadedPlugin(descriptor, PluginState.CYCLE)
            logger.warn("插件 {} 处于循环依赖中，已跳过", id)
        }

        // 先拦掉"要的 API 比当前启动器新"的插件：宁可明确拒绝，也不要跑到一半抛 NoSuchMethodError
        val incompatible = mutableSetOf<String>()
        for (id in result.order) {
            val descriptor = byId.getValue(id)
            val required = descriptor.plugin.apiLevel
            when {
                required > ApiLevel.CURRENT -> {
                    incompatible += id
                    loaded[id] = LoadedPlugin(descriptor, PluginState.INCOMPATIBLE_API)
                    logger.error(
                        "插件 {} 需要 API {}，当前启动器是 {}，已拒绝加载（请更新启动器或换用旧版插件）",
                        id,
                        required,
                        ApiLevel.CURRENT,
                    )
                    events.publish(PluginFailed(id, "api"))
                }

                required == 0 ->
                    logger.warn("插件 {} 未声明 API 级别（按兼容处理），建议升级到声明 apiLevel 的版本", id)
            }
        }

        // 依赖了被拒绝插件的，按缺失依赖处理，避免半启动
        for (id in result.order) {
            if (id in incompatible) continue
            val descriptor = byId.getValue(id)
            val blocked = descriptor.dependencies.filter { dependency -> dependency in incompatible }
            if (blocked.isNotEmpty()) {
                loaded[id] = LoadedPlugin(descriptor, PluginState.MISSING_DEP)
                logger.warn("插件 {} 依赖不受支持的插件 {}，已跳过", id, blocked)
            }
        }

        for (id in result.order) {
            val descriptor = byId.getValue(id)
            if (loaded[id]?.state == PluginState.INCOMPATIBLE_API) continue
            if (loaded[id]?.state == PluginState.MISSING_DEP) continue
            try {
                descriptor.plugin.onLoad(createContext(descriptor))
                loaded[id] = LoadedPlugin(descriptor, PluginState.LOADED)
                logger.info("插件 {} {} 已加载", descriptor.id, descriptor.version)
            } catch (t: Throwable) {
                loaded[id] = LoadedPlugin(descriptor, PluginState.FAILED, t)
                logger.error("插件 {} 在 onLoad 阶段失败，已隔离", id, t)
                events.publish(PluginFailed(id, "load"))
            }
        }

        for (id in result.order) {
            val entry = loaded[id] ?: continue
            if (entry.state != PluginState.LOADED) continue
            try {
                entry.descriptor.plugin.onEnable()
                loaded[id] = entry.copy(state = PluginState.ENABLED)
                logger.info("插件 {} 已启用", id)
                events.publish(PluginEnabled(id, entry.version))
            } catch (t: Throwable) {
                loaded[id] = entry.copy(state = PluginState.FAILED, error = t)
                logger.error("插件 {} 在 onEnable 阶段失败，已隔离", id, t)
                events.publish(PluginFailed(id, "enable"))
            }
        }
    }

    /** 逆序禁用全部插件并关闭类加载器；可重复调用。 */
    override fun close() {
        if (closed) return
        closed = true
        for (entry in loaded.values.toList().asReversed()) {
            if (entry.state == PluginState.ENABLED || entry.state == PluginState.LOADED) {
                try {
                    entry.descriptor.plugin.onDisable()
                    loaded[entry.id] = entry.copy(state = PluginState.DISABLED)
                    logger.info("插件 {} 已禁用", entry.id)
                    events.publish(PluginDisabled(entry.id))
                } catch (t: Throwable) {
                    loaded[entry.id] = entry.copy(state = PluginState.FAILED, error = t)
                    logger.error("插件 {} 在 onDisable 阶段失败，已隔离", entry.id, t)
                    events.publish(PluginFailed(entry.id, "disable"))
                }
            }
            runCatching { entry.descriptor.classLoader?.close() }
                .onFailure { logger.warn("关闭插件类加载器失败：{}", entry.id, it) }
        }
    }

    private fun createContext(descriptor: PluginDescriptor): PluginContext {
        val dataDir = launcher.dataDir.resolve("plugin-data").resolve(descriptor.id)
        Files.createDirectories(dataDir)
        return PluginContextImpl(
            pluginId = descriptor.id,
            dataDir = dataDir,
            logger = LoggerFactory.getLogger("dev.mmcl.plugin.${descriptor.id}"),
            events = events,
            services = services,
            settings = settings,
            launcher = launcher,
        )
    }
}
