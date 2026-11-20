package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.app.appearance.SkinAvatar
import dev.mmcl.app.appearance.SkinLoader
import dev.mmcl.app.auth.activeAccount
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.ui.UiSlot
import dev.mmcl.api.ui.UiWidget
import dev.mmcl.app.NavigationIntent
import dev.mmcl.core.instance.Instance
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold

/**
 * 首页：账户 / 下载源状态 + 最近实例 + 快捷入口。
 *
 * 「启动」不在首页执行：设置 [NavigationIntent] 后跳到游戏页，由游戏页原有逻辑启动，
 * 这样日志、停止、缺文件检查都只有一份实现。
 */
class HomePage(
    private val instances: InstanceService? = null,
    private val manifests: VersionManifestService? = null,
    private val authProviders: List<AuthProvider> = emptyList(),
    private val activeProviderId: () -> String? = { null },
    private val playerName: String = "Player",
    private val skinLoader: SkinLoader? = null,
    private val intent: NavigationIntent? = null,
    private val widgets: List<UiWidget> = emptyList(),
    private val pluginFolder: PluginFolder? = null,
) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "首页"
    override val titleKey: String = "page.home.title"
    override val icon: UiIcon = UiIcon.HOME
    override val group: UiGroup = UiGroup.MAIN
    override val order: Int = -1

    @Composable
    override fun Content(navigator: AppNavigator) {
        val strings = LocalStrings.current
        var recent by remember { mutableStateOf<List<Instance>>(emptyList()) }

        LaunchedEffect(Unit) {
            recent = instances?.recent(RECENT_LIMIT).orEmpty()
        }

        val account = authProviders.activeAccount(activeProviderId())
        val sourceCount = manifests?.orderedSources()?.size ?: 0

        PageScaffold(title = strings.t("page.home.title"), subtitle = strings.t("home.subtitle")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(MmclSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MmclSpacing.l),
            ) {
                SkinAvatar(
                    loader = skinLoader,
                    urlProvider = { account?.skinUrl },
                    name = account?.accountName ?: playerName,
                    size = 40.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.t(
                    "home.account",
                    account?.displayName ?: strings.t("home.account.no_auth"),
                    account?.accountName ?: playerName,
                ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (account == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (sourceCount > 0) strings.t("home.sources.installed", sourceCount) else strings.t("home.sources.none"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sourceCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = { navigator.navigate(SettingsPage.PAGE_ID) }) { Text(strings.t("settings.title")) }
            }

            if (sourceCount == 0) {
                NoSourceGuide(folder = pluginFolder)
            }

            if (recent.isEmpty()) {
                EmptyState(
                    icon = UiIcon.GAME,
                    text = strings.t("home.empty.title"),
                    hint = strings.t("home.empty.hint"),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.t("home.recent"), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { navigator.navigate(InstancesPage.PAGE_ID) }) { Text(strings.t("home.all_instances")) }
                }
                recent.forEach { instance ->
                    RecentInstanceRow(
                        instance = instance,
                        onLaunch = {
                            intent?.requestLaunch(instance.id)
                            navigator.navigate(InstancesPage.PAGE_ID)
                        },
                    )
                }
            }

            Text(strings.t("home.shortcuts"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s)) {
                Button(onClick = { navigator.navigate(DownloadsPage.PAGE_ID) }) { Text(strings.t("home.install_new")) }
                OutlinedButton(onClick = { navigator.navigate(DownloadsPage.PAGE_ID) }) { Text(strings.t("page.downloads.title")) }
                OutlinedButton(onClick = { navigator.navigate(PluginsPage.PAGE_ID) }) { Text(strings.t("page.plugins.title")) }
            }

            val homeWidgets = widgets.filter { it.slot == UiSlot.HOME }.sortedBy { it.order }
            if (homeWidgets.isNotEmpty()) {
                Text(strings.t("home.widgets"), style = MaterialTheme.typography.titleSmall)
                homeWidgets.forEach { widget ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(MmclSpacing.m),
                    ) {
                        widget.Content(navigator)
                    }
                }
            }
        }
    }

    companion object {
        private const val RECENT_LIMIT = 3

        /** 页面 id。 */
        const val PAGE_ID: String = "dev.mmcl.page.home"
    }
}

@Composable
private fun RecentInstanceRow(instance: Instance, onLaunch: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(MmclSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = instance.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = strings.t(
                    "home.instance.version",
                    instance.versionId,
                    if (instance.lastLaunchedAtEpochMillis > 0) {
                        strings.t("home.instance.recent")
                    } else {
                        strings.t("home.instance.never")
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onLaunch) { Text(strings.t("home.instance.launch")) }
    }
}
