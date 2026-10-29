package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.core.plugin.LoadedPlugin
import dev.mmcl.core.plugin.PluginState
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold

/** 插件页：展示插件加载状态与来源 JAR，插件管理（启用/禁用）留到后续版本。 */
class PluginsPage(private val plugins: List<LoadedPlugin>) : UiPage {
    override val id: String = "dev.mmcl.page.plugins"
    override val title: String = "插件"
    override val icon: UiIcon = UiIcon.PLUGIN
    override val group: UiGroup = UiGroup.SYSTEM
    override val order: Int = 20

    @Composable
    override fun Content(navigator: AppNavigator) {
        PageScaffold(title = "插件", subtitle = "共 ${plugins.size} 个，数据目录下的 plugins/ 与内置插件同一条加载路径") {
            if (plugins.isEmpty()) {
                EmptyState(icon = UiIcon.PLUGIN, text = "没有发现插件", hint = "把插件 JAR 放进 <数据目录>/plugins/ 后重启")
                return@PageScaffold
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(MmclSpacing.s)) {
                items(plugins, key = { it.id }) { plugin ->
                    PluginRow(plugin)
                }
            }
        }
    }
}

@Composable
private fun PluginRow(plugin: LoadedPlugin) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(MmclSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = plugin.id, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${plugin.version} · ${plugin.descriptor.jar?.fileName ?: "内置"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StateChip(plugin.state)
    }
}

@Composable
private fun StateChip(state: PluginState) {
    val container =
        when (state) {
            PluginState.ENABLED -> MaterialTheme.colorScheme.secondaryContainer
            PluginState.FAILED -> MaterialTheme.colorScheme.errorContainer
            PluginState.MISSING_DEP, PluginState.CYCLE -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val content =
        when (state) {
            PluginState.ENABLED -> MaterialTheme.colorScheme.onSecondaryContainer
            PluginState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
            PluginState.MISSING_DEP, PluginState.CYCLE -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(color = container, shape = RoundedCornerShape(percent = 50)) {
        Text(
            text = state.name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}
