package dev.mmcl.app.pages

import androidx.compose.runtime.Composable
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.PageScaffold

/** 下载页：等下载源插件（Modrinth / CurseForge）与下载引擎落地。 */
class DownloadsPage : UiPage {
    override val id: String = "dev.mmcl.page.downloads"
    override val title: String = "下载"
    override val icon: UiIcon = UiIcon.DOWNLOAD
    override val group: UiGroup = UiGroup.MAIN
    override val order: Int = 10

    @Composable
    override fun Content(navigator: AppNavigator) {
        PageScaffold(title = "下载", subtitle = "游戏 / 模组 / 整合包") {
            EmptyState(
                icon = UiIcon.DOWNLOAD,
                text = "下载中心还没接上",
                hint = "应用不内置任何下载源：需要安装下载源插件后才能下载游戏文件",
            )
        }
    }
}
