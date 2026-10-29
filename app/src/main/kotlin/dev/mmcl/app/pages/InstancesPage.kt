package dev.mmcl.app.pages

import androidx.compose.runtime.Composable
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.PageScaffold

/** 游戏页：0.0.1 阶段先占位，0.1.x 的实例与启动链路落地后填真实数据。 */
class InstancesPage : UiPage {
    override val id: String = "dev.mmcl.page.instances"
    override val title: String = "游戏"
    override val icon: UiIcon = UiIcon.GAME
    override val group: UiGroup = UiGroup.MAIN
    override val order: Int = 0

    @Composable
    override fun Content(navigator: AppNavigator) {
        PageScaffold(title = "游戏", subtitle = "实例与版本") {
            EmptyState(
                icon = UiIcon.GAME,
                text = "还没有实例",
                hint = "启动链落地后，这里会列出本机实例并提供启动按钮",
            )
        }
    }
}
