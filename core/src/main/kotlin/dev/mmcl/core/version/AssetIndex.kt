package dev.mmcl.core.version

import kotlinx.serialization.Serializable

/**
 * 资源索引（`assets/indexes/<id>.json`）子集。
 *
 * `objects` 的 key 是资源逻辑路径（如 `minecraft/textures/...`），
 * 下载时只需要 [AssetObject.hash] 与大小。
 */
@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
)

/** 一个资源对象。 */
@Serializable
data class AssetObject(
    val hash: String = "",
    val size: Long = 0,
)
