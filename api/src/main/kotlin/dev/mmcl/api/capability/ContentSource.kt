package dev.mmcl.api.capability

/** 内容类型。 */
enum class ContentKind {
    MOD,
    RESOURCE_PACK,
    SHADER_PACK,
    MODPACK,
}

/**
 * 内容搜索条件。
 *
 * @param query 关键词（空串表示浏览/按条件筛选）
 * @param gameVersion 目标 Minecraft 版本，例如 `26.3`
 * @param loader 加载器（`fabric` / `neoforge` / `forge`）；资源包等可为 `null`
 * @param kind 内容类型
 * @param limit 返回上限
 */
data class ContentSearchQuery(
    val query: String,
    val gameVersion: String,
    val loader: String? = null,
    val kind: ContentKind = ContentKind.MOD,
    val limit: Int = 20,
)

/** 搜索结果里的一个项目。 */
data class ContentProject(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val downloads: Long = 0,
)

/** 一个可下载文件。 */
data class ContentFile(
    val fileName: String,
    val url: String,
    val size: Long = 0,
    val sha1: String? = null,
    val sha512: String? = null,
    val primary: Boolean = true,
)

/** 版本依赖。 */
data class ContentDependency(
    /** 依赖的项目 id；插件只给 versionId 时可为 `null`。 */
    val projectId: String? = null,
    val versionId: String? = null,
    val fileName: String? = null,
    /** 是否必需；optional / incompatible 不自动安装。 */
    val required: Boolean = true,
)

/** 一个具体版本。 */
data class ContentVersion(
    val id: String,
    val projectId: String? = null,
    val name: String = "",
    val versionNumber: String = "",
    val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val files: List<ContentFile> = emptyList(),
    val dependencies: List<ContentDependency> = emptyList(),
)

/**
 * 内容来源扩展点（Modrinth / CurseForge / 自定义源）。
 *
 * 插件只负责搜索、列版本与返回文件地址/哈希；下载、依赖解析、写盘由启动器负责，
 * 与版本源一样，应用不内置任何内容地址。
 */
interface ContentSource {
    /** 全局唯一标识，例如 `modrinth`。 */
    val id: String

    /** 展示名。 */
    val displayName: String

    /** 按条件搜索项目。 */
    suspend fun search(query: ContentSearchQuery): List<ContentProject>

    /** 列出项目对目标游戏版本/加载器可用的版本（最新在前）。 */
    suspend fun versions(projectId: String, gameVersion: String, loader: String?): List<ContentVersion>

    /** 按版本 id 取详情，用于解析依赖；找不到返回 `null`。 */
    suspend fun version(versionId: String): ContentVersion?
}
