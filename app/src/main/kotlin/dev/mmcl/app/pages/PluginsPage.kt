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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.mmcl.core.plugin.PluginConflict
import dev.mmcl.core.plugin.PluginConflictReason
import dev.mmcl.core.plugin.PluginSignature
import dev.mmcl.core.plugin.PluginState
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclIcons
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold

/**
 * 插件页：展示加载状态、来源 JAR、完整性与冲突。
 *
 * 点击状态标签可启用/禁用插件：写入 `plugins.disabled`，**重启后生效**
 * （运行中的插件不做热卸载，避免服务与页面指针失效）。
 */
class PluginsPage(
    private val plugins: List<LoadedPlugin>,
    private val conflicts: List<PluginConflict> = emptyList(),
    private val disabledIdsProvider: () -> Set<String> = { emptySet() },
    private val onToggle: (String, Boolean) -> Unit = { _, _ -> },
) : UiPage {
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
        var disabled by remember { mutableStateOf(disabledIdsProvider()) }
        var changed by remember { mutableStateOf(false) }
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
                if (changed) {
                    item {
                        Text(
                            text = strings.t("plugins.restart_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                items(plugins, key = { it.id }) { plugin ->
                    val toggleable = plugin.state == PluginState.ENABLED || plugin.state == PluginState.DISABLED
                    PluginRow(
                        plugin = plugin,
                        disabled = plugin.id in disabled,
                        onToggle =
                            if (toggleable) {
                                {
                                    val enable = plugin.id in disabled
                                    disabled = if (enable) disabled - plugin.id else disabled + plugin.id
                                    changed = true
                                    onToggle(plugin.id, enable)
                                }
                            } else {
                                null
                            },
                    )
                }
                if (conflicts.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.t("plugins.conflicts.title"),
                            modifier = Modifier.padding(top = MmclSpacing.m),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    items(conflicts, key = { "conflict-${it.jar}" }) { conflict ->
                        PluginConflictRow(conflict)
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRow(plugin: LoadedPlugin, disabled: Boolean = false, onToggle: (() -> Unit)? = null) {
    val strings = LocalStrings.current
    val meta = plugin.descriptor.plugin
    val integrity = plugin.descriptor.integrity
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
            plugin.descriptor.plugin.minLauncherVersion
                ?.takeIf { it.isNotBlank() && plugin.state == PluginState.INCOMPATIBLE_LAUNCHER }
                ?.let { required ->
                    Text(
                        text = strings.t("plugins.requires_launcher", required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            if (integrity != null) {
                Text(
                    text =
                        buildString {
                            append(strings.t("plugins.integrity.sha"))
                            append(" ")
                            append(integrity.shortSha256)
                            append(" · ")
                            append(
                                when (val signature = integrity.signature) {
                                    PluginSignature.Unsigned -> strings.t("plugins.integrity.unsigned")
                                    is PluginSignature.Signed ->
                                        strings.t(
                                            "plugins.integrity.signed",
                                            signature.signers.firstOrNull() ?: "",
                                        )
                                    is PluginSignature.Invalid -> strings.t("plugins.integrity.invalid")
                                },
                            )
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (integrity.signature is PluginSignature.Unsigned) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }
        }
        // 禁用/重新启用的即时反馈：重启前先按用户的选择显示
        val displayState =
            when {
                disabled -> PluginState.DISABLED
                plugin.state == PluginState.DISABLED -> PluginState.ENABLED
                else -> plugin.state
            }
        StateChip(displayState, onClick = onToggle)
    }
}

@Composable
private fun StateChip(state: PluginState, onClick: (() -> Unit)? = null) {
    val strings = LocalStrings.current
    val container =
        when (state) {
            PluginState.ENABLED -> MaterialTheme.colorScheme.secondaryContainer
            PluginState.FAILED -> MaterialTheme.colorScheme.errorContainer
            PluginState.MISSING_DEP, PluginState.CYCLE, PluginState.INCOMPATIBLE_LAUNCHER ->
                MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val content =
        when (state) {
            PluginState.ENABLED -> MaterialTheme.colorScheme.onSecondaryContainer
            PluginState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
            PluginState.MISSING_DEP, PluginState.CYCLE, PluginState.INCOMPATIBLE_LAUNCHER ->
                MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val label = strings.t("plugin.state.${state.name}")
    if (onClick != null) {
        // 可点击：启用/禁用开关（重启生效）
        Surface(onClick = onClick, color = container, shape = RoundedCornerShape(percent = 50)) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    } else {
        Surface(color = container, shape = RoundedCornerShape(percent = 50)) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
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

/** 被忽略的插件 JAR：版本冲突、签名无效或读取失败。 */
@Composable
private fun PluginConflictRow(conflict: PluginConflict) {
    val strings = LocalStrings.current
    val reason =
        when (conflict.reason) {
            PluginConflictReason.DUPLICATE -> strings.t("plugins.conflict.duplicate")
            PluginConflictReason.INVALID_SIGNATURE -> strings.t("plugins.conflict.invalid_signature")
            PluginConflictReason.LOAD_FAILED -> strings.t("plugins.conflict.load_failed")
        }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .padding(MmclSpacing.m),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = buildString {
                append(conflict.jar.fileName)
                conflictIdentity(conflict)?.let { identity ->
                    append(" · ")
                    append(identity)
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = buildString {
                append(reason)
                conflict.detail?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(it)
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `id version` 形式的冲突标识；两者都没有时返回 `null`。 */
private fun conflictIdentity(conflict: PluginConflict): String? =
    listOfNotNull(conflict.id, conflict.version).takeIf { it.isNotEmpty() }?.joinToString(" ")
