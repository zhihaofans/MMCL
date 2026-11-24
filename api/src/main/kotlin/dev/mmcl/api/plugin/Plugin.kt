package dev.mmcl.api.plugin

/**
 * 启动器插件契约。
 *
 * 实现类必须在 `META-INF/services/dev.mmcl.api.plugin.Plugin` 中声明，
 * 由 `core` 通过 [java.util.ServiceLoader] 发现。插件只依赖 `api`，
 * 不允许依赖 `core` 或其它插件（见 `AGENTS.md` §3）。
 *
 * 生命周期：`onLoad` → `onEnable` → … 运行中 … → `onDisable`。
 * 生命周期回调中的异常会被 core 隔离并记录，不会中断其它插件。
 */
interface Plugin {
    /** 全局唯一标识，小写反向域名，例如 `dev.mmcl.auth.microsoft`。 */
    val id: String

    /** 语义化版本，例如 `1.0.0`。 */
    val version: String

    /** 展示名（界面上代替 [id] 显示）；不提供时用 [id]。 */
    val title: String
        get() = id

    /** 作者名；不提供时界面不显示作者。 */
    val author: String
        get() = ""

    /** 图标语义，由 `app` 映射成内置矢量图；默认是通用插件图标。 */
    val icon: PluginIcon
        get() = PluginIcon.DEFAULT

    /**
     * 兼容的最低启动器版本（例如 `0.0.8`）；缺省表示不限制。
     *
     * 启动器版本低于该值时插件会被拒绝加载（状态 `INCOMPATIBLE_LAUNCHER`），
     * 用于依赖较新启动器能力（如新扩展点、页面 API）的插件。
     */
    val minLauncherVersion: String?
        get() = null

    /**
     * 编译该插件时使用的 API 级别，见 [dev.mmcl.api.ApiLevel.CURRENT]。
     *
     * 新插件请写 `override val apiLevel: Int = ApiLevel.CURRENT`；
     * `0` 表示未声明（旧插件），按兼容处理并打一条告警；
     * 声明值高于启动器当前级别时，插件会被拒绝加载（`INCOMPATIBLE_API`）。
     */
    val apiLevel: Int
        get() = 0

    /** 硬依赖：缺失时本插件不会被加载。 */
    val dependencies: List<String>
        get() = emptyList()

    /** 软依赖：缺失不影响加载，仅用于声明可选的协作关系。 */
    val optionalDependencies: List<String>
        get() = emptyList()

    /**
     * 注入上下文并注册服务。
     *
     * 此回调应当快速返回：不要在这里做网络请求或大文件 IO。
     *
     * @param context 该插件专属的上下文，生命周期与插件一致
     */
    fun onLoad(context: PluginContext) {}

    /** 依赖已全部启用后调用，可以开始真正的工作。 */
    fun onEnable() {}

    /** 释放资源、取消订阅；必须幂等。 */
    fun onDisable() {}
}
