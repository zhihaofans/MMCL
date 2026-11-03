package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.core.plugin.LoadedPlugin
import dev.mmcl.core.plugin.PluginState
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclIcons
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold

/** 插件页：展示插件加载状态与来源 JAR，插件管理（启用/禁用）留到后续版本。 */
class PluginsPage(private val plugins: List<LoadedPlugin>) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "插件"
    override val titleKey: String = "page.plugins.title"
    override val icon: UiIcon = UiIcon.PLUGIN
    override val group: UiGroup = UiGroup.SYSTEM
    override val order: Int = 20

    companion object {
        /** 页面 id。 */
        const val PAGE_ID: String = "dev.mmcl.page.plugins"
    }

    @Composable
    override fun Content(navigator: AppNavigator) {
        val strings = LocalStrings.current
        PageScaffold(title = strings.t("page.plugins.title"), subtitle = strings.t("plugins.subtitle", plugins.size)) {
            if (plugins.isEmpty()) {
                EmptyState(
                    icon = UiIcon.PLUGIN,
                    text = strings.t("plugins.empty.title"),
                    hint = strings.t("plugins.empty.hint"),
                )
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
    val meta = plugin.descriptor.plugin
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(MmclSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MmclIcons.of(meta.icon.toUiIcon()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = meta.title.ifBlank { plugin.id }, style = MaterialTheme.typography.titleSmall)
            Text(
                text = buildString {
                    append(plugin.id)
                    append(" · ")
                    append(plugin.version)
                    if (meta.author.isNotBlank()) {
                        append(" · ")
                        append(meta.author)
                    }
                },
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

/** 把插件自报的图标语义映射成内置矢量图；未知取值走通用图标。 */
private fun PluginIcon.toUiIcon(): UiIcon =
    when (this) {
        PluginIcon.AUTH -> UiIcon.ACCOUNT
        PluginIcon.SOURCE -> UiIcon.DOWNLOAD
        PluginIcon.RUNTIME -> UiIcon.SETTINGS
        PluginIcon.THEME -> UiIcon.EXTENSION
        PluginIcon.UI -> UiIcon.PLUGIN
        PluginIcon.TOOL -> UiIcon.WRENCH
        PluginIcon.LOCALE -> UiIcon.INFO
        PluginIcon.DEFAULT -> UiIcon.PLUGIN
    }
