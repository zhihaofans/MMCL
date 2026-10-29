package dev.mmcl.api.service

/**
 * 轻量键值设置存储，由 core 实现并注入到 [dev.mmcl.api.plugin.PluginContext]。
 *
 * 约定：
 * - 键用点分命名空间，例如 `ui.theme`、`dev.mmcl.theme.sakura.enabled`；
 * - 值是字符串，复杂结构由拥有该键的插件自行序列化（JSON 等）；
 * - 写入后应立即持久化，跨启动保留；
 * - 值不能包含换行符（实现基于 properties 文件）。
 */
interface SettingsService {
    /**
     * 读取设置。
     *
     * @param key 设置键
     * @param default 不存在时返回的默认值
     */
    fun getString(key: String, default: String? = null): String?

    /**
     * 写入设置；传 `null` 表示删除该键。
     */
    fun putString(key: String, value: String?)
}
