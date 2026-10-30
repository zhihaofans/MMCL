package dev.mmcl.core.version

import kotlinx.serialization.Serializable

/**
 * 单个版本的详情 JSON（`versions/<id>/<id>.json`）子集。
 *
 * 只保留启动与下载需要的字段；未知字段由 JSON 配置忽略。
 * 注意：这里的所有 URL 都来自清单数据（也就是插件提供的源），core 不含任何硬编码地址。
 */
@Serializable
data class VersionDetail(
    val id: String,
    val type: String = "release",
    val mainClass: String = "",
    val inheritsFrom: String? = null,
    val assets: String = "",
    val assetIndex: AssetIndexRef? = null,
    val downloads: Map<String, DownloadEntry> = emptyMap(),
    val libraries: List<Library> = emptyList(),
    val logging: Logging? = null,
    val javaVersion: JavaVersion? = null,
) {
    /** 客户端主 JAR 的下载信息。 */
    val client: DownloadEntry? get() = downloads["client"]
}

/**
 * 一个可下载条目。
 *
 * @property path 仅库文件使用：相对 `libraries/` 的路径
 * @property id 仅日志配置使用：配置文件名（如 `client-1.21.8.xml`）
 */
@Serializable
data class DownloadEntry(
    val url: String = "",
    val sha1: String = "",
    val size: Long = 0,
    val path: String? = null,
    val id: String = "",
)

/** 资源索引（`assets/indexes/<id>.json`）的下载信息。 */
@Serializable
data class AssetIndexRef(
    val id: String = "",
    val sha1: String = "",
    val size: Long = 0,
    val url: String = "",
)

/**
 * 一个库条目。
 *
 * @property natives 老版本的 natives 映射（1.19 之前），当前只做识别不做下载
 */
@Serializable
data class Library(
    val name: String = "",
    val downloads: LibraryDownloads = LibraryDownloads(),
    val rules: List<LibraryRule> = emptyList(),
    val natives: Map<String, String> = emptyMap(),
)

/** 库的下载信息。 */
@Serializable
data class LibraryDownloads(
    val artifact: DownloadEntry? = null,
    val classifiers: Map<String, DownloadEntry> = emptyMap(),
)

/**
 * 库的限定规则，语义与官方启动器一致：
 * 按顺序匹配，命中的最后一条决定 allow / deny；没有任何规则时视为允许。
 */
@Serializable
data class LibraryRule(
    val action: String = "allow",
    val os: OsRule? = null,
)

/** 规则里的操作系统限定。 */
@Serializable
data class OsRule(
    val name: String? = null,
    val arch: String? = null,
)

/** 日志配置。 */
@Serializable
data class Logging(
    val client: LoggingClient? = null,
)

/** 客户端日志配置：`file` 是 log4j 配置文件，`argument` 里用 `${path}` 占位。 */
@Serializable
data class LoggingClient(
    val argument: String = "",
    val file: DownloadEntry? = null,
    val type: String = "",
)

/** 该版本需要的 Java 版本信息（用于运行时检测 / 下载）。 */
@Serializable
data class JavaVersion(
    val component: String = "",
    val majorVersion: Int = 0,
)
