package dev.mmcl.core.download

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.version.AssetIndex
import dev.mmcl.core.version.VersionDetail
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.core.version.VersionManifestEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
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

/** 安装结果：规划 + 下载报告（游戏文件与资源对象合并计数）。 */
data class InstallResult(
    val plan: InstallPlan,
    val report: DownloadReport,
    /** 资源对象下载报告；当前源不支持资源下载时为 `null`。 */
    val assetReport: DownloadReport? = null,
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
    private val arch: Architecture,
    private val logger: Logger,
    /** 资源对象地址提供者；由来源插件决定，默认不支持。 */
    private val assetUrl: (hash: String) -> String? = { null },
    /** 普通文件地址改写钩子；由来源插件的 `resolveUrl` 提供，默认不改写。 */
    private val resolveUrl: (String) -> String? = { null },
) : Installer {
    private val json = Json { ignoreUnknownKeys = true }

    /** 只做规划，不下载（界面用于"检查文件"）。 */
    suspend fun plan(entry: VersionManifestEntry, forceRefresh: Boolean = false): InstallPlan {
        val detail = detailService.load(entry, forceRefresh)
        val planned = GameFilePlanner.plan(detail, os, arch, resolveUrl)
        planned.warnings.forEach { logger.warn("版本 {}：{}", detail.id, it) }
        return InstallPlan(detail = detail, files = planned.files, warnings = planned.warnings)
    }

    /**
     * 下载资源对象（`assets/objects`）。
     *
     * 资源索引文件由 [plan] 阶段下载到 `assets/indexes/<id>.json`；这里解析它，
     * 再按来源插件给的地址逐个下载。当前源不提供地址时返回 `null` 并告警。
     */
    private suspend fun downloadAssets(
        detail: VersionDetail,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): DownloadReport? {
        val indexRef = detail.assetIndex ?: return null
        val indexFile = root.resolve("assets/indexes/${indexRef.id}.json")
        if (!Files.isRegularFile(indexFile)) {
            logger.warn("资源索引不存在，跳过资源对象下载：{}", indexFile)
            return null
        }
        val index =
            runCatching { json.decodeFromString(AssetIndex.serializer(), Files.readString(indexFile, Charsets.UTF_8)) }
                .onFailure { logger.warn("资源索引解析失败：{}", it.message) }
                .getOrNull()
                ?: return null
        val assetPlan = AssetPlanner.plan(index) { hash -> assetUrl(hash) }
        if (assetPlan.missingUrls > 0) {
            logger.warn("{} 个资源对象没有下载地址：当前来源插件不支持资源对象下载", assetPlan.missingUrls)
        }
        if (assetPlan.files.isEmpty()) {
            logger.warn("没有可下载的资源对象，游戏可能因缺贴图崩溃")
            return null
        }
        logger.info("开始下载资源对象：{} 个文件", assetPlan.files.size)
        return downloader.download(root, assetPlan.files) { progress -> onProgress(progress.copy(phase = "assets")) }
    }

    /** 规划并下载全部文件；已存在且校验通过的文件会被跳过。 */
    override suspend fun install(
        entry: VersionManifestEntry,
        forceRefresh: Boolean,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): InstallResult {
        val plan = plan(entry, forceRefresh)
        logger.info("开始准备版本 {}：{} 个文件，共 {} 字节", plan.versionId, plan.fileCount, plan.totalBytes)
        val fileReport = downloader.download(root, plan.files) { progress -> onProgress(progress.copy(phase = "files")) }
        val assetReport = downloadAssets(plan.detail, onProgress)
        val merged =
            DownloadReport(
                downloaded = fileReport.downloaded + (assetReport?.downloaded ?: 0),
                skipped = fileReport.skipped + (assetReport?.skipped ?: 0),
                failures = fileReport.failures + (assetReport?.failures.orEmpty()),
            )
        logger.info(
            "版本 {} 准备完成：下载 {}，跳过 {}，失败 {}（资源对象：{}）",
            plan.versionId,
            merged.downloaded,
            merged.skipped,
            merged.failures.size,
            if (assetReport == null) "未启用" else "下载 ${assetReport.downloaded} / 跳过 ${assetReport.skipped}",
        )
        return InstallResult(plan, merged, assetReport)
    }

    /** 便利重载：不强制刷新、不需要进度。 */
    suspend fun install(entry: VersionManifestEntry): InstallResult = install(entry, forceRefresh = false) {}

    /** 便利重载：只关心进度。 */
    suspend fun install(
        entry: VersionManifestEntry,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): InstallResult = install(entry, forceRefresh = false, onProgress = onProgress)
}
