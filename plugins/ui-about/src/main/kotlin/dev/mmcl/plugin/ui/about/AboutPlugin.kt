package dev.mmcl.plugin.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.ui.UiSlot
import dev.mmcl.api.ui.UiWidget
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold

/**
 * 示例 UI 页面：演示插件如何往导航栏加一页（`dev.mmcl.api.ui.UiPage`）。
 */
class AboutPage(private val launcher: LauncherInfo) : UiPage {
    override val id: String = "dev.mmcl.ui.about"
    override val title: String = "关于"
    override val icon: UiIcon = UiIcon.INFO
    override val group: UiGroup = UiGroup.TOOLS
    override val order: Int = 200

    @Composable
    override fun Content(navigator: AppNavigator) {
        PageScaffold(title = "关于 MMCL", subtitle = "内置示例 UI 插件") {
            Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.xs)) {
                Text(text = "启动器版本：${launcher.version}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "运行平台：${launcher.os} / ${launcher.arch}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "数据目录：${launcher.dataDir}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "这一页由插件通过 UiPage 注册，用来验证插件可以往左侧导航栏追加页面。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 注册 [AboutPage] 的插件入口。 */
class AboutPlugin : Plugin {
    override val id: String = "dev.mmcl.ui.about.plugin"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT

    override fun onLoad(context: PluginContext) {
        context.services.register(UiPage::class, AboutPage(context.launcher))
        context.services.register(UiWidget::class, AboutHomeWidget(context.launcher))
        context.services.register(UiWidget::class, AboutStatusWidget())
        context.logger.info("关于页与插槽小部件已注册")
    }
}


/** 首页卡片：演示插件往 [UiSlot.HOME] 挂内容。 */
class AboutHomeWidget(private val launcher: LauncherInfo) : UiWidget {
    override val id: String = "dev.mmcl.ui.about.home"
    override val slot: UiSlot = UiSlot.HOME
    override val order: Int = 100

    @Composable
    override fun Content(navigator: AppNavigator) {
        Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.xs)) {
            Text(text = "MMCL ${launcher.version}", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "这张卡片由 ui-about 插件通过 UiWidget 注册，用来验证首页插槽。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 状态栏标记：演示插件往 [UiSlot.STATUS_BAR] 挂内容。 */
class AboutStatusWidget : UiWidget {
    override val id: String = "dev.mmcl.ui.about.status"
    override val slot: UiSlot = UiSlot.STATUS_BAR
    override val order: Int = 100

    @Composable
    override fun Content(navigator: AppNavigator) {
        Text(
            text = "about 插件",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
