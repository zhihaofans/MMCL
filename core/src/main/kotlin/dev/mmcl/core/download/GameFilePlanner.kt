package dev.mmcl.core.download

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.version.Library
import dev.mmcl.core.version.LibraryRule
import dev.mmcl.core.version.VersionDetail
import dev.mmcl.core.version.mavenPathOf
import dev.mmcl.core.version.resolvedArtifact

/**
 * 把版本详情转换成"需要哪些文件"的清单。
 *
 * 纯函数，不做 IO：
 * - 客户端 JAR、资源索引、日志配置：详情里自带 URL；
 * - 库文件：按 `rules` 过滤平台，再按库名里的架构标识过滤（`natives-macos` vs `natives-macos-arm64`）；
 * - 同一 base 存在架构专用变体时，丢弃没有架构标识的通用变体（避免 classpath 先命中错架构）；
 * - `natives` / `classifiers`（1.19 之前的老版本）暂不下载，只记录警告；
 * - 资源对象（`assets/objects/<hash>`）不在本步处理 —— 地址基址需要由来源插件提供。
 */
object GameFilePlanner {
    /** 规划结果：要下载的文件 + 被跳过的说明。 */
    data class Plan(
        val files: List<PlannedFile>,
        val warnings: List<String>,
    )

    /**
     * 规划单个版本需要下载的文件。
     *
     * @param os 目标平台，用于解析库的 `rules`
     * @param arch 目标架构，用于剔除 `natives-*-arm64` / `natives-*-x86_64` 里不匹配的变体
     */
    /** 返回（下载地址, 回退地址）；被改写时回退地址是原地址，否则为 `null`。 */
    private fun plannedUrl(original: String, resolveUrl: (String) -> String?): Pair<String, String?> {
        val mirrored = resolveUrl(original)
        return if (mirrored != null && mirrored != original) mirrored to original else original to null
    }

    fun plan(
        detail: VersionDetail,
        os: OperatingSystem,
        arch: Architecture,
        /** 下载地址改写钩子；`null` 表示按 JSON 原地址下载。 */
        resolveUrl: (String) -> String? = { null },
    ): Plan {
        val files = mutableListOf<PlannedFile>()
        val warnings = mutableListOf<String>()

        detail.client?.takeIf { it.url.isNotBlank() }?.let { client ->
            files +=
                PlannedFile(
                    relativePath = "versions/${detail.baseVersionId}/${detail.baseVersionId}.jar",
                    url = plannedUrl(client.url, resolveUrl).first,
                    fallbackUrl = plannedUrl(client.url, resolveUrl).second,
                    sha1 = client.sha1,
                    size = client.size,
                )
        }

        detail.assetIndex?.takeIf { it.url.isNotBlank() && it.id.isNotBlank() }?.let { index ->
            files +=
                PlannedFile(
                    relativePath = "assets/indexes/${index.id}.json",
                    url = plannedUrl(index.url, resolveUrl).first,
                    fallbackUrl = plannedUrl(index.url, resolveUrl).second,
                    sha1 = index.sha1,
                    size = index.size,
                )
        }

        detail.logging?.client?.file?.takeIf { it.url.isNotBlank() }?.let { config ->
            val name =
                config.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: config.id.takeIf { it.isNotBlank() }
                    ?: config.url.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: "client-${detail.id}.xml"
            files +=
                PlannedFile(
                    relativePath = "assets/log_configs/$name",
                    url = plannedUrl(config.url, resolveUrl).first,
                    fallbackUrl = plannedUrl(config.url, resolveUrl).second,
                    sha1 = config.sha1,
                    size = config.size,
                )
        }

        for (library in allowedLibraries(detail, os, arch)) {
            val artifact = library.resolvedArtifact()
            if (artifact == null) {
                if (library.natives.isNotEmpty() || library.downloads.classifiers.isNotEmpty()) {
                    warnings += "跳过 ${library.name}：natives/classifiers 下载暂未支持"
                }
                continue
            }
            if (artifact.url.isBlank()) continue
            val path = artifact.path?.takeIf { it.isNotBlank() } ?: mavenPathOf(library.name) ?: continue
            files +=
                PlannedFile(
                    relativePath = "libraries/$path",
                    url = plannedUrl(artifact.url, resolveUrl).first,
                    fallbackUrl = plannedUrl(artifact.url, resolveUrl).second,
                    sha1 = artifact.sha1,
                    size = artifact.size,
                )
        }

        return Plan(files.sortedBy { it.relativePath }, warnings)
    }

    /**
     * 过滤出当前平台/架构需要的库（下载与 classpath 共用同一份结果）。
     */
    fun allowedLibraries(detail: VersionDetail, os: OperatingSystem, arch: Architecture): List<Library> {
        val candidates = detail.libraries.filter { allows(it.rules, os) && archMatches(it.name, arch) }

        // 同一个 base 若有架构专用 natives 变体，就丢掉无架构标识的通用变体
        val natives = candidates.filter { classifierOf(it.name)?.contains("natives") == true }
        val dropNames =
            natives
                .groupBy { baseOf(it.name) }
                .values
                .filter { group -> group.any { archTokenOf(it.name) != null } }
                .flatMap { group -> group.filter { archTokenOf(it.name) == null }.map { it.name } }
                .toSet()

        return candidates.filterNot { it.name in dropNames }
    }

    /**
     * 计算库规则是否允许当前平台，语义与官方启动器一致：
     * 没有任何规则 → 允许；否则按顺序匹配，最后一条命中的规则决定结果。
     */
    fun allows(rules: List<LibraryRule>, os: OperatingSystem): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (!rule.matches(os)) continue
            allowed = rule.action != "deny"
        }
        return allowed
    }

    private fun LibraryRule.matches(targetOs: OperatingSystem): Boolean {
        val rule = this.os ?: return true
        return when (rule.name?.lowercase()) {
            null, "" -> true
            "osx", "macos" -> targetOs == OperatingSystem.MACOS
            "windows" -> targetOs == OperatingSystem.WINDOWS
            "linux" -> targetOs == OperatingSystem.LINUX
            else -> false
        }
    }

    private fun archMatches(libraryName: String, arch: Architecture): Boolean {
        val token = archTokenOf(libraryName) ?: return true
        return token == arch
    }

    /** 从库名里的 classifier 推断架构，例如 `natives-macos-arm64` → [Architecture.ARM64]。 */
    internal fun archTokenOf(libraryName: String): Architecture? {
        val classifier = classifierOf(libraryName) ?: return null
        return when {
            classifier.contains("arm64") -> Architecture.ARM64
            classifier.contains("x86_64") || classifier.contains("amd64") -> Architecture.X86_64
            else -> null
        }
    }

    private fun classifierOf(libraryName: String): String? =
        libraryName.split(':').getOrNull(3)?.takeIf { it.isNotBlank() }

    private fun baseOf(libraryName: String): String = libraryName.split(':').take(3).joinToString(":")

    /** `group:artifact:version[:classifier]` → Maven 相对路径。 */
}
