package dev.mmcl.api.capability

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem

/** 运行时压缩包格式。 */
enum class ArchiveFormat {
    ZIP,
    TAR_GZ,
}

/**
 * 一个可下载的 Java 运行时。
 *
 * @property url 下载地址（由插件决定；应用不内置任何地址）
 * @property sha1 压缩包的 SHA-1；未知时为 `null`，此时只靠 TLS 与解压后的版本校验
 * @property format 压缩格式
 * @property fileName 保存到磁盘时的文件名（可选）
 */
data class RuntimeDownload(
    val url: String,
    val sha1: String? = null,
    val format: ArchiveFormat = ArchiveFormat.ZIP,
    val fileName: String? = null,
)

/**
 * Java 运行时来源扩展点。
 *
 * 应用自身不下载 Java：没有安装本类插件时，缺少运行时只能由用户手动安装 JDK。
 */
interface JavaRuntimeProvider {
    /** 全局唯一标识，例如 `adoptium`。 */
    val id: String

    /** 展示名。 */
    val displayName: String

    /**
     * 解析某个大版本在目标平台上的下载信息。
     *
     * @return 不支持该平台/版本时返回 `null`
     */
    suspend fun resolve(majorVersion: Int, os: OperatingSystem, arch: Architecture): RuntimeDownload?
}
