package dev.mmcl.app.appearance

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import java.nio.file.Files
import java.nio.file.Path

/**
 * 把用户选的背景图读成 [ImageBitmap]。
 *
 * 读不出来（文件损坏、不是图片）返回 `null`，界面退回纯色背景——
 * 一张坏图不应该让启动器起不来。
 */
fun loadBackgroundBitmap(path: Path?): ImageBitmap? {
    if (path == null) return null
    return runCatching { Files.newInputStream(path).use { loadImageBitmap(it) } }.getOrNull()
}
