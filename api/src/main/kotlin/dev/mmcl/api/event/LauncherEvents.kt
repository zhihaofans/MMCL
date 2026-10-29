package dev.mmcl.api.event

/** 启动器内置事件的共同标记。 */
sealed interface LauncherEvent

/**
 * 游戏即将启动。
 *
 * @property instanceId 实例标识
 * @property versionId Minecraft 版本标识
 */
data class GameLaunching(
    val instanceId: String,
    val versionId: String,
) : LauncherEvent

/**
 * 游戏进程已退出。
 *
 * @property instanceId 实例标识
 * @property exitCode 进程退出码
 * @property durationMillis 运行时长（毫秒）
 */
data class GameExited(
    val instanceId: String,
    val exitCode: Int,
    val durationMillis: Long,
) : LauncherEvent

/**
 * 插件已启用。
 *
 * @property pluginId 插件 id
 * @property version 插件版本
 */
data class PluginEnabled(
    val pluginId: String,
    val version: String,
) : LauncherEvent

/**
 * 插件已禁用。
 *
 * @property pluginId 插件 id
 */
data class PluginDisabled(
    val pluginId: String,
) : LauncherEvent

/**
 * 插件生命周期回调抛出异常（已被隔离）。
 *
 * @property pluginId 插件 id
 * @property phase 出错阶段：`load` / `enable` / `disable`
 */
data class PluginFailed(
    val pluginId: String,
    val phase: String,
) : LauncherEvent
