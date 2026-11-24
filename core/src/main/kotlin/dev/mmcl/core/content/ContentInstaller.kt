package dev.mmcl.core.content

import dev.mmcl.api.capability.ContentSource
import dev.mmcl.api.capability.ContentVersion
import dev.mmcl.core.download.DownloadReport
import dev.mmcl.core.download.FileDownloader
import dev.mmcl.core.download.PlannedFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger

/** 一次内容安装请求。 */
data class ContentInstallRequest(
    val source: ContentSource,
    /** 实例游戏目录（mods 在它下面）。 */
    val instanceDir: Path,
    val gameVersion: String,
    val loader: String?,
    val version: ContentVersion,
)

/** 安装结果。 */
data class ContentInstallResult(
    /** 实际解析出的版本（含自动依赖）。 */
    val versions: List<ContentVersion>,
    val report: DownloadReport,
    /** 因为已存在而跳过的文件名。 */
    val skippedExisting: List<String> = emptyList(),
    /** 无法解析的依赖（不阻断其它文件）。 */
    val unresolvedDependencies: List<String> = emptyList(),
)

/**
 * 内容安装：解析必需依赖 → 规划到实例 `mods/` → 下载并校验。
 *
 * 已存在的同名文件不会被覆盖（避免误伤用户手动装的 mod），只跳过并报告。
 * 依赖解析有深度与去重保护，缺失依赖只记录，不阻断已能解析的部分。
 *
 * @param downloader 复用游戏文件下载器（重试、限速、SHA-1 校验）
 */
class ContentInstaller(
    private val downloader: FileDownloader,
    private val logger: Logger,
    private val maxDependencyDepth: Int = 8,
) {
    suspend fun install(request: ContentInstallRequest): ContentInstallResult {
        val resolution = resolveDependencies(request)
        val resolved = resolution.versions
        val files = mutableListOf<PlannedFile>()
        val skipped = mutableListOf<String>()
        val root = request.instanceDir.toAbsolutePath().normalize()

        for (version in resolved) {
            val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull() ?: continue
            val relative = "mods/${file.fileName}"
            val target = root.resolve(relative).normalize()
            if (!target.startsWith(root)) {
                logger.warn("内容文件名包含非法路径，已跳过：{}", file.fileName)
                continue
            }
            if (Files.isRegularFile(target)) {
                skipped += file.fileName
                continue
            }
            files +=
                PlannedFile(
                    relativePath = relative,
                    url = file.url,
                    sha1 = file.sha1.orEmpty(),
                    size = file.size,
                )
        }

        val report =
            if (files.isEmpty()) {
                DownloadReport(downloaded = 0, skipped = 0, failures = emptyList())
            } else {
                withContext(Dispatchers.IO) { downloader.download(request.instanceDir, files) }
            }
        logger.info(
            "内容安装完成：{} 个版本，下载 {}，跳过 {}，失败 {}",
            resolved.size,
            report.downloaded,
            report.skipped + skipped.size,
            report.failures.size,
        )
        return ContentInstallResult(
            versions = resolved,
            report = report,
            skippedExisting = skipped,
            unresolvedDependencies = resolution.unresolved,
        )
    }

    private data class Resolution(
        val versions: List<ContentVersion>,
        val unresolved: List<String>,
    )

    private suspend fun resolveDependencies(request: ContentInstallRequest): Resolution {
        val result = mutableListOf<ContentVersion>()
        val seenVersions = mutableSetOf<String>()
        val seenProjects = mutableSetOf<String>()
        val unresolved = mutableListOf<String>()

        suspend fun walk(version: ContentVersion, depth: Int) {
            if (depth > maxDependencyDepth) {
                logger.warn("依赖层级超过 {}，停止解析：{}", maxDependencyDepth, version.id)
                return
            }
            if (!seenVersions.add(version.id)) return
            val projectKey = version.projectId
            if (projectKey != null && !seenProjects.add(projectKey)) return
            result += version

            for (dependency in version.dependencies.filter { it.required }) {
                val resolved =
                    try {
                        resolveDependency(request, dependency.projectId, dependency.versionId)
                    } catch (e: IOException) {
                        logger.warn("解析依赖失败：{}（{}）", dependency.projectId ?: dependency.versionId, e.message)
                        null
                    }
                if (resolved == null) {
                    unresolved += dependency.projectId ?: dependency.versionId ?: dependency.fileName ?: "?"
                    continue
                }
                walk(resolved, depth + 1)
            }
        }

        walk(request.version, 0)
        if (unresolved.isNotEmpty()) {
            logger.warn("有 {} 个依赖未能解析：{}", unresolved.size, unresolved)
        }
        return Resolution(result, unresolved)
    }

    private suspend fun resolveDependency(
        request: ContentInstallRequest,
        projectId: String?,
        versionId: String?,
    ): ContentVersion? {
        if (versionId != null) {
            request.source.version(versionId)?.let { return it }
        }
        val project = projectId ?: return null
        return request.source.versions(project, request.gameVersion, request.loader).firstOrNull()
    }
}
