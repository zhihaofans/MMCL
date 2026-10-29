package dev.mmcl.plugin.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
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

    override fun onLoad(context: PluginContext) {
        context.services.register(UiPage::class, AboutPage(context.launcher))
        context.logger.info("关于页已注册")
    }
}
