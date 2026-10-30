package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.LauncherTheme
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.app.theme.ThemeController
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold

/**
 * 设置页：主题切换（即时生效并持久化）+ 运行信息。
 *
 * 主题列表 = 内置主题 + 插件注册的主题；点击即调用 [ThemeController.apply]。
 */
class SettingsPage(
    private val launcher: LauncherInfo,
    private val themeController: ThemeController,
    private val safeMode: Boolean,
    private val manifests: VersionManifestService? = null,
) : UiPage {
    override val id: String = "dev.mmcl.page.settings"
    override val title: String = "设置"
    override val icon: UiIcon = UiIcon.SETTINGS
    override val group: UiGroup = UiGroup.SYSTEM
    override val order: Int = 30

    @Composable
    override fun Content(navigator: AppNavigator) {
        PageScaffold(title = "设置", subtitle = "外观与运行信息") {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
            ) {
                SectionTitle("外观")
                val currentId = themeController.current.id
                themeController.all().forEach { theme ->
                    ThemeRow(theme = theme, selected = theme.id == currentId) { themeController.apply(theme.id) }
                }
                if (safeMode) {
                    Text(
                        text = "安全模式已开启：已跳过全部插件主题与插件页面。去掉 --safe-mode 与 MMCL_SAFE_MODE 后重启即可恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = MmclSpacing.s),
                    )
                }

                SectionTitle("下载源")
                val sources = manifests?.orderedSources().orEmpty()
                if (sources.isEmpty()) {
                    Text(
                        text = "未安装版本源插件。本应用不内置任何下载地址：把 source-mojang 等插件放进 <数据目录>/plugins/ 后重启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    var selectedSourceId by remember(manifests) { mutableStateOf(sources.first().id) }
                    for (source in sources) {
                        SourceRow(
                            source = source,
                            selected = source.id == selectedSourceId,
                            onClick = {
                                selectedSourceId = source.id
                                manifests?.selectSource(source.id)
                            },
                        )
                    }
                    Text(
                        text = "缓存来源：${manifests?.cachedSourceId() ?: "无"}；选中后下一次刷新生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle("关于")
                InfoRow("版本", launcher.version)
                InfoRow("平台", "${launcher.os} / ${launcher.arch}")
                InfoRow("数据目录", launcher.dataDir.toString())
            }
        }
    }
}

@Composable
private fun SourceRow(source: VersionManifestSource, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MmclSpacing.s, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.xs),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${source.id} · 优先级 ${source.priority}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = MmclSpacing.m),
    )
}

@Composable
private fun ThemeRow(theme: LauncherTheme, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MmclSpacing.s, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.xs),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = theme.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = theme.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (theme.dark) "深色" else "浅色",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.m),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(84.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
