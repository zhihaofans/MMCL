package dev.mmcl.api.capability

/**
 * 版本清单来源扩展点。
 *
 * **启动器不内置任何下载源**：清单从哪个地址拉取完全由插件决定（Mojang 官方、镜像、
 * 内网源、本地文件等）。没有安装源插件时，启动器不会发起任何游戏文件相关的网络请求，
 * 只能启动本机已经装好的实例。
 *
 * 实现只负责"供货"：返回清单 JSON 原文。解析、字段校验、缓存、回退顺序全部由 core 负责，
 * 因此插件返回脏数据只会导致该源被跳过，不会污染缓存。
 *
 * 通过 `ServiceRegistry.register(VersionManifestSource::class, ...)` 注册；
 * 同一时刻可以装多个源，core 按 [priority] 升序尝试，全部失败才回退到本地缓存。
 */
interface VersionManifestSource {
    /** 全局唯一标识，例如 `mojang`、`bmclapi`、`local`。 */
    val id: String

    /** 设置页展示名，例如 `Mojang 官方`。 */
    val displayName: String

    /** 优先级，越小越先尝试；同优先级按 id 排序保证顺序稳定。 */
    val priority: Int
        get() = 100

    /**
     * 拉取 `version_manifest_v2.json` 原文。
     *
     * 失败时抛任意异常即可，core 会记录并把该源跳过、继续尝试下一个。
     */
    suspend fun fetchManifestJson(): String

    /**
     * 资源对象（`assets/objects/<hash>`）的下载地址。
     *
     * 应用内不硬编码资源基址：由来源插件决定怎么映射。
     * 返回 `null` 表示该源不支持资源对象下载（游戏会因缺贴图崩溃）。
     *
     * @param hash 资源对象的 SHA-1
     */
    fun assetObjectUrl(hash: String): String? = null
}
