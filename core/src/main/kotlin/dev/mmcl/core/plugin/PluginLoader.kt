package dev.mmcl.core.plugin

import dev.mmcl.api.plugin.Plugin
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import org.slf4j.Logger

/**
 * 从插件目录发现插件（`ServiceLoader` + 每 JAR 一个独立 `URLClassLoader`）。
 *
 * 隔离约定（`ARCHITECTURE.md` §9、§12.4）：
 * - 一个 JAR 一个类加载器，插件之间不能直接引用对方类；
 * - 单个 JAR 加载失败只记录日志，不影响其它插件；
 * - 同一 id 出现多版本时保留较高版本并告警（`ARCHITECTURE.md` §10）。
 *
 * @param logger 加载日志
 * @param parentClassLoader 插件类加载器的父加载器，默认是 `api` 所在的加载器
 */
class PluginLoader(
    private val logger: Logger,
    private val parentClassLoader: ClassLoader = Plugin::class.java.classLoader,
) {
    /**
     * 扫描目录并返回去重后的插件描述符。
     *
     * @param pluginsDir 插件目录，不存在时返回空列表
     */
    fun discover(pluginsDir: Path): List<PluginDescriptor> {
        if (!Files.isDirectory(pluginsDir)) {
            logger.info("插件目录不存在，跳过扫描：{}", pluginsDir)
            return emptyList()
        }

        val jars =
            Files.list(pluginsDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                    .sorted()
                    .toList()
            }

        val byId = LinkedHashMap<String, PluginDescriptor>()
        for (jar in jars) {
            var classLoader: URLClassLoader? = null
            try {
                classLoader = URLClassLoader(arrayOf(jar.toUri().toURL()), parentClassLoader)
                val declared = ServiceLoader.load(Plugin::class.java, classLoader).toList()
                if (declared.isEmpty()) {
                    logger.warn("插件 JAR 未声明 META-INF/services/{}：{}", Plugin::class.java.name, jar)
                    classLoader.close()
                    continue
                }
                val created = mutableListOf<PluginDescriptor>()
                for (plugin in declared) {
                    val id = plugin.id.trim()
                    val version = plugin.version.trim()
                    if (id.isEmpty() || version.isEmpty()) {
                        logger.error("插件 id 或 version 为空，已跳过：{}", jar)
                        continue
                    }
                    created +=
                        PluginDescriptor(
                            id = id,
                            version = version,
                            dependencies = plugin.dependencies,
                            optionalDependencies = plugin.optionalDependencies,
                            jar = jar,
                            classLoader = classLoader,
                            plugin = plugin,
                        )
                }
                for (descriptor in created) {
                    val existing = byId[descriptor.id]
                    when {
                        existing == null -> {
                            byId[descriptor.id] = descriptor
                            logger.info("发现插件 {} {}：{}", descriptor.id, descriptor.version, jar.fileName)
                        }

                        compareVersions(descriptor.version, existing.version) > 0 -> {
                            logger.warn(
                                "插件 {} 存在多版本，保留 {}，忽略 {}",
                                descriptor.id,
                                descriptor.version,
                                existing.version,
                            )
                            byId[descriptor.id] = descriptor
                        }

                        else -> logger.warn(
                            "插件 {} 存在多版本，保留已发现的 {}，忽略 {}",
                            descriptor.id,
                            existing.version,
                            descriptor.version,
                        )
                    }
                }
                if (created.none { byId[it.id] === it }) {
                    classLoader.close()
                }
            } catch (t: Throwable) {
                // 插件自身的 ServiceLoader 配置错误也要被隔离，不能拖垮启动流程
                logger.error("加载插件 JAR 失败，已隔离：{}", jar, t)
                runCatching { classLoader?.close() }
            }
        }
        return byId.values.toList()
    }

    /**
     * 比较两个语义化版本，返回值语义与 [Comparable.compareTo] 一致。
     *
     * 仅比较数字段；`-SNAPSHOT` 之类的后缀在 0.x 阶段不参与比较。
     */
    internal fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.', '-', '+')
        val rightParts = right.split('.', '-', '+')
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val a = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
            val b = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
