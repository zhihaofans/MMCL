package dev.mmcl.app.pages

import dev.mmcl.core.version.VersionManifest

/** 版本清单的界面状态；游戏页与下载页共用。 */
internal sealed interface ManifestUiState {
    data object Idle : ManifestUiState

    data object Loading : ManifestUiState

    data class Loaded(val manifest: VersionManifest) : ManifestUiState

    data class Failed(val message: String) : ManifestUiState
}
