package dev.mmcl.core.plugin

/** 插件在运行时可能处于的状态。 */
enum class PluginState {
    /** 已发现，尚未执行 `onLoad`。 */
    DISCOVERED,

    /** `onLoad` 成功。 */
    LOADED,

    /** `onEnable` 成功，正在工作。 */
    ENABLED,

    /** 已正常禁用。 */
    DISABLED,

    /** 硬依赖缺失，未加载。 */
    MISSING_DEP,

    /** 处于循环依赖中，未加载。 */
    CYCLE,

    /** 插件声明的 API 级别高于当前启动器，拒绝加载。 */
    INCOMPATIBLE_API,

    /** 插件要求的最低启动器版本高于当前版本，拒绝加载。 */
    INCOMPATIBLE_LAUNCHER,

    /** 生命周期回调抛出异常，已隔离。 */
    FAILED,
}
