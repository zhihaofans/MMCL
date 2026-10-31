package dev.mmcl.core.download

import dev.mmcl.core.version.VersionManifestEntry

/**
 * 安装一个版本的抽象，便于 [DownloadsService] 在测试里替换。
 *
 * 生产实现是 [GameInstaller]。
 */
fun interface Installer {
    /**
     * 规划并下载某个版本，已存在且校验通过的文件会被跳过。
     *
     * @param forceRefresh 是否强制重新拉取版本详情
     * @param onProgress 进度回调（在下载协程里调用）
     */
    suspend fun install(
        entry: VersionManifestEntry,
        forceRefresh: Boolean,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): InstallResult
}
