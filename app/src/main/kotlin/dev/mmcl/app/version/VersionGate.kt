package dev.mmcl.app.version

import dev.mmcl.core.version.VersionManifest
import dev.mmcl.core.version.VersionSupport

/**
 * 版本是否允许启动/修复。
 *
 * 产品边界：只支持最新正式版 + 同主版本系列。清单里查得到的旧大版本（例如最新是
 * `26.x` 时的 `1.21.11`）一律拒绝，界面上给明确提示；清单里没有的版本（离线/缓存场景）
 * 无法判断类型，按放行处理，避免误伤本机已有实例。
 *
 * @param versionId 实例使用的版本 id
 * @param manifest 当前清单；没有下载源时为 `null`
 */
fun isLaunchable(versionId: String, manifest: VersionManifest?): Boolean {
    if (manifest == null) return true
    val entry = manifest.find(versionId) ?: return true
    return VersionSupport.isSupported(entry, VersionSupport.latestSeries(manifest))
}

/** 受支持的版本列表（向导用）；[limit] 为 0 表示不截断。 */
fun supportedReleases(
    manifest: VersionManifest,
    limit: Int = 6,
): List<dev.mmcl.core.version.VersionManifestEntry> {
    val series = VersionSupport.latestSeries(manifest)
    val releases = manifest.versions.filter { VersionSupport.isSupported(it, series) }
    return if (limit > 0) releases.take(limit) else releases
}
