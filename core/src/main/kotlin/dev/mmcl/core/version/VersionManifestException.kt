package dev.mmcl.core.version

/**
 * 版本清单相关的失败（网络、HTTP 状态码、JSON 解析等）。
 */
open class VersionManifestException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 没有安装任何版本源插件，且本地没有可用缓存。
 *
 * 界面据此提示用户去安装下载源插件（`plugins/source-mojang` 等），
 * 而不是让启动器悄悄连官方地址。
 */
class NoVersionSourceException : VersionManifestException(
    "没有可用的版本清单源：请把版本源插件（如 source-mojang）放进 <数据目录>/plugins/ 后重启",
)
