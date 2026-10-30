package dev.mmcl.core.download

import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.version.VersionDetail
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.core.version.VersionManifestEntry
import java.nio.file.Path
import org.slf4j.Logger

/**
 * 一次安装的规划结果。
 *
 * @property detail 版本详情（启动时还要用 mainClass / javaVersion 等字段）
 * @property files 需要下载的文件
 * @property warnings 跳过项说明（natives 等）
 */
data class InstallPlan(
    val detail: VersionDetail,
    val files: List<PlannedFile>,
    val warnings: List<String>,
) {
    /** 版本 id。 */
    val versionId: String get() = detail.id

    /** 文件数量。 */
    val fileCount: Int get() = files.size

    /** 全部文件的总大小（字节，按清单声明值累加）。 */
    val totalBytes: Long get() = files.sumOf { it.size }
}

/** 安装结果：规划 + 下载报告。 */
data class InstallResult(
    val plan: InstallPlan,
    val report: DownloadReport,
)

/**
 * 把"版本清单 → 版本详情 → 文件规划 → 并发下载校验"串成一条链。
 *
 * 只做机制：所有 URL 都来自清单与版本详情（源头是用户安装的来源插件），
 * 这里不包含任何下载地址。
 *
 * @param root 游戏根目录（`<dataDir>`）
 * @param os 目标平台，用于过滤平台相关的库
 */
class GameInstaller(
    private val detailService: VersionDetailService,
    private val downloader: FileDownloader,
    private val root: Path,
    private val os: OperatingSystem,
    private val logger: Logger,
) {
    /** 只做规划，不下载（界面用于"检查文件"）。 */
    suspend fun plan(entry: VersionManifestEntry, forceRefresh: Boolean = false): InstallPlan {
        val detail = detailService.load(entry, forceRefresh)
        val planned = GameFilePlanner.plan(detail, os)
        planned.warnings.forEach { logger.warn("版本 {}：{}", detail.id, it) }
        return InstallPlan(detail = detail, files = planned.files, warnings = planned.warnings)
    }

    /** 规划并下载全部文件；已存在且校验通过的文件会被跳过。 */
    suspend fun install(
        entry: VersionManifestEntry,
        forceRefresh: Boolean = false,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): InstallResult {
        val plan = plan(entry, forceRefresh)
        logger.info("开始准备版本 {}：{} 个文件，共 {} 字节", plan.versionId, plan.fileCount, plan.totalBytes)
        val report = downloader.download(root, plan.files, onProgress)
        logger.info(
            "版本 {} 准备完成：下载 {}，跳过 {}，失败 {}",
            plan.versionId,
            report.downloaded,
            report.skipped,
            report.failures.size,
        )
        return InstallResult(plan, report)
    }
}
