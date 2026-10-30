package dev.mmcl.core.version

/**
 * 版本支持范围：只支持**最新正式版 + 同主版本系列**。
 *
 * - 系列定义：`26.3` → `26`；`1.21.8` → `1.21`（旧式版本号以 `1.` 开头，取前两段）；
 * - 只有正式版（`release`）才算受支持，快照 / old_beta / old_alpha 一律不支持；
 * - 旧大版本（例如最新是 `26.x` 时的 `1.21.x`）不保证，界面上标注但不提供下载/启动。
 *
 * 这条策略是产品决定，不是实现限制：放宽时改这里 + 文档即可。
 */
object VersionSupport {
    /** 取版本所属的系列，例如 `26.3` → `26`、`1.21.8` → `1.21`、`26.4-snapshot-6` → `26`。 */
    fun seriesOf(versionId: String): String {
        val trimmed = versionId.trim()
        if (trimmed.isEmpty()) return trimmed
        val parts = trimmed.split('.', '-', '+')
        val major = parts.firstOrNull { it.isNotEmpty() } ?: return trimmed
        return if (major == "1" && parts.size >= 2 && parts[1].isNotEmpty()) "1.${parts[1]}" else major
    }

    /** 当前受支持的系列（由清单里的最新正式版决定）。 */
    fun latestSeries(manifest: VersionManifest): String = seriesOf(manifest.latest.release)

    /** 该版本是否在支持范围内。 */
    fun isSupported(version: VersionManifestEntry, supportedSeries: String): Boolean =
        version.isRelease && seriesOf(version.id) == supportedSeries
}
