package dev.mmcl.core.version

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Mojang 版本清单（`version_manifest_v2.json` 的子集）。
 *
 * 只保留启动器真正需要的字段，未知字段由 [kotlinx.serialization.json.Json] 忽略。
 *
 * @property latest 最新的正式版与快照版本号
 * @property versions 全部版本条目，按官方顺序（新的在前）
 */
@Serializable
data class VersionManifest(
    val latest: LatestVersions,
    val versions: List<VersionManifestEntry>,
) {
    /** 按 id 查找版本条目。 */
    fun find(versionId: String): VersionManifestEntry? = versions.firstOrNull { it.id == versionId }

    /** 最新正式版条目；清单异常时可能为 `null`。 */
    fun latestRelease(): VersionManifestEntry? = find(latest.release)

    /** 最新快照条目；清单异常时可能为 `null`。 */
    fun latestSnapshot(): VersionManifestEntry? = find(latest.snapshot)

    /** 只看正式版。 */
    fun releases(): List<VersionManifestEntry> = versions.filter { it.isRelease }
}

/**
 * 最新版本号。
 *
 * @property release 最新正式版，例如 `1.21.8`
 * @property snapshot 最新快照，例如 `25w31a`
 */
@Serializable
data class LatestVersions(
    val release: String,
    val snapshot: String,
)

/**
 * 一个版本条目。
 *
 * @property id 版本号，例如 `1.21.8`
 * @property type `release` / `snapshot` / `old_beta` / `old_alpha`
 * @property url 该版本详情 JSON 的地址（后续下载游戏文件用）
 * @property sha1 详情 JSON 的 SHA-1
 * @property releaseTime 正式发布时间（ISO-8601）
 * @property time 清单更新时间（ISO-8601）
 */
@Serializable
data class VersionManifestEntry(
    val id: String,
    val type: String,
    val url: String,
    val sha1: String = "",
    val releaseTime: String = "",
    val time: String = "",
) {
    /** 是否为正式版。 */
    val isRelease: Boolean
        get() = type == TYPE_RELEASE

    /** 解析 [releaseTime]；格式异常时返回 `null`，不让脏数据炸掉界面。 */
    fun releaseInstant(): Instant? = runCatching { Instant.parse(releaseTime) }.getOrNull()

    companion object {
        /** 正式版类型标识。 */
        const val TYPE_RELEASE: String = "release"
    }
}
