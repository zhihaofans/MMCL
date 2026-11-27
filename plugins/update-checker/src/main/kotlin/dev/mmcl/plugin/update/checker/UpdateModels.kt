package dev.mmcl.plugin.update.checker

import kotlinx.serialization.Serializable

@Serializable
internal data class UpdateIndexDto(
    val launcher: LauncherDto? = null,
    val plugins: List<PluginDto> = emptyList(),
)

@Serializable
internal data class LauncherDto(
    val version: String = "",
    val tag: String = "",
    val releaseUrl: String = "",
)

@Serializable
internal data class PluginDto(
    val id: String = "",
    val version: String = "",
    val file: String = "",
    val sha256: String = "",
)

/** 本地已安装插件。 */
internal data class InstalledPlugin(
    val id: String,
    val version: String,
    val file: String,
)

/** 插件更新状态。 */
internal sealed interface PluginState {
    data class UpToDate(val installed: InstalledPlugin, val remote: PluginDto) : PluginState

    data class UpdateAvailable(val installed: InstalledPlugin?, val remote: PluginDto) : PluginState
}

/** 一次检查的完整结果。 */
internal data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val latestTag: String,
    val releaseUrl: String,
    val indexAvailable: Boolean,
    val plugins: List<PluginState>,
) {
    val launcherUpdateAvailable: Boolean
        get() = compareVersions(latestVersion, currentVersion) > 0
}

/** 解析 `<pluginId>-<version>.jar`；不匹配返回 `null`。 */
internal fun pluginFileInfo(fileName: String): InstalledPlugin? {
    val base = fileName.removeSuffix(".jar")
    val match = Regex("""^(.+)-(\d+\.\d+\.\d+)$""").find(base) ?: return null
    return InstalledPlugin(id = match.groupValues[1], version = match.groupValues[2], file = fileName)
}

/** 简单语义化版本比较（只比数字段；预发布标记忽略）。 */
internal fun compareVersions(left: String, right: String): Int {
    val leftParts = left.removePrefix("v").split('.', '-', '+')
    val rightParts = right.removePrefix("v").split('.', '-', '+')
    val size = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until size) {
        val a = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
        val b = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
        if (a != b) return a.compareTo(b)
    }
    return 0
}
