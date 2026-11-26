package dev.mmcl.app.platform

import java.awt.Desktop
import java.net.URI

/** 用系统默认浏览器打开链接；失败时返回原因供界面显示。 */
object LinkOpener {
    fun open(url: String): Result<Unit> =
        runCatching {
            val desktop = Desktop.getDesktop()
            if (!Desktop.isDesktopSupported() || !desktop.isSupported(Desktop.Action.BROWSE)) {
                error("当前平台不支持打开浏览器")
            }
            desktop.browse(URI.create(url))
        }
}
