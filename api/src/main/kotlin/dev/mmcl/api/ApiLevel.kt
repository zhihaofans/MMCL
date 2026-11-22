package dev.mmcl.api

/**
 * 插件 API 的二进制兼容级别。
 *
 * 只有在**删改已有接口 / 方法签名或语义**、会让已编译插件跑不动的情况下才 `+1`；
 * 新增带默认实现的方法、给数据类加带默认值的字段都不需要动它。
 *
 * 插件用 [CURRENT] 声明自己按哪一级 API 编译（见 `Plugin.apiLevel`），
 * core 会拒绝加载声明级别高于当前启动器的插件。
 */
object ApiLevel {
    /**
     * 当前 API 级别。历史：
     * - 1：初始级别
     * - 2：`LoaderInstaller` 重做（`listVersions` + `install` 返回 overlay，删除旧 `install`）
     */
    const val CURRENT: Int = 2
}
