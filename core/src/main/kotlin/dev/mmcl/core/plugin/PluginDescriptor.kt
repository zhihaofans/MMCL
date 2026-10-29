package dev.mmcl.core.plugin

import dev.mmcl.api.plugin.Plugin
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * 插件加载期元数据：从插件实例上读出的声明信息 + 隔离用的类加载器。
 *
 * @property id 插件 id
 * @property version 插件版本
 * @property dependencies 硬依赖 id 列表
 * @property optionalDependencies 软依赖 id 列表
 * @property jar 插件 JAR 路径；测试或内置构造时可为 `null`
 * @property classLoader 该插件专属类加载器；测试或内置构造时可为 `null`
 * @property plugin 插件实例
 */
data class PluginDescriptor(
    val id: String,
    val version: String,
    val dependencies: List<String> = emptyList(),
    val optionalDependencies: List<String> = emptyList(),
    val jar: Path? = null,
    val classLoader: URLClassLoader? = null,
    val plugin: Plugin,
)
