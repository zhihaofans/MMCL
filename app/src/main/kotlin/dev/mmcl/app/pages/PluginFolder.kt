package dev.mmcl.app.pages

import java.nio.file.Path

/**
 * 插件目录的展示与打开入口，由 `Main` 组装。
 *
 * 页面只负责展示和触发，平台相关的打开方式留在 core 的 `DirectoryLauncher`。
 *
 * @property path 插件目录（`<dataDir>/plugins`）
 * @property open 打开目录，成功或失败原因由调用方显示
 */
data class PluginFolder(
    val path: Path,
    val open: () -> Result<Unit>,
)
