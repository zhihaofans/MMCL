package dev.mmcl.plugin.update.checker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.LocalLauncherLocale
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.launch

/**
 * 「更新」页：检查启动器本体与插件更新。
 *
 * 启动器只显示新版本并打开发布页（自动安装要等平台签名/公证）；
 * 非内置插件可直接下载新 JAR（SHA-256 校验）并提示重启。
 */
internal class UpdatePage(
    private val checker: UpdateChecker,
) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "更新"
    override val icon: UiIcon = UiIcon.INFO
    override val group: UiGroup = UiGroup.SYSTEM
    override val order: Int = 25

    @Composable
    override fun LocalizedTitle(): String = UpdateTexts.t(LocalLauncherLocale.current, "page.title")

    @Composable
    override fun Content(navigator: AppNavigator) {
        val locale = LocalLauncherLocale.current
        val scope = rememberCoroutineScope()
        var checking by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<UpdateCheckResult?>(null) }
        var message by remember { mutableStateOf<String?>(null) }
        var busyPlugin by remember { mutableStateOf<String?>(null) }

        fun checkNow() {
            checking = true
            message = null
            scope.launch {
                try {
                    result = checker.check()
                } catch (t: Throwable) {
                    message = UpdateTexts.t(locale, "update.failed", t.message ?: t::class.simpleName.orEmpty())
                } finally {
                    checking = false
                }
            }
        }

        fun openRelease(url: String) {
            runCatching {
                if (!Desktop.isDesktopSupported()) error("当前平台不支持打开浏览器")
                Desktop.getDesktop().browse(URI.create(url))
            }.onFailure {
                message = UpdateTexts.t(locale, "update.failed", it.message ?: it::class.simpleName.orEmpty())
            }
        }

        fun install(remote: PluginDto, tag: String) {
            busyPlugin = remote.id
            message = null
            scope.launch {
                checker
                    .install(remote, tag)
                    .onSuccess {
                        message = UpdateTexts.t(locale, "plugin.installed", it.fileName)
                        result = runCatching { checker.check() }.getOrNull()
                    }
                    .onFailure {
                        message = UpdateTexts.t(locale, "plugin.failed", it.message ?: it::class.simpleName.orEmpty())
                    }
                busyPlugin = null
            }
        }

        PageScaffold(
            title = UpdateTexts.t(locale, "page.title"),
            actions = {
                Button(onClick = { checkNow() }, enabled = !checking) {
                    Text(
                        if (checking) {
                            UpdateTexts.t(locale, "checking")
                        } else {
                            UpdateTexts.t(locale, "check")
                        },
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MmclSpacing.m),
            ) {
                Text(
                    text = UpdateTexts.t(locale, "current", checker.currentVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val current = result
                if (current == null) {
                    if (checking) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(MmclSpacing.xl))
                            Spacer(modifier = Modifier.size(MmclSpacing.s))
                            Text(UpdateTexts.t(locale, "checking"))
                        }
                    }
                    return@Column
                }

                Text(
                    text =
                        if (current.launcherUpdateAvailable) {
                            UpdateTexts.t(
                                locale,
                                "launcher_update",
                                current.currentVersion,
                                current.latestVersion,
                            )
                        } else {
                            UpdateTexts.t(locale, "up_to_date")
                        },
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (current.launcherUpdateAvailable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Text(
                    text = UpdateTexts.t(locale, "latest", current.latestVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current.launcherUpdateAvailable) {
                    OutlinedButton(onClick = { openRelease(current.releaseUrl) }) {
                        Text(UpdateTexts.t(locale, "open_release"))
                    }
                }

                HorizontalDivider()
                Text(UpdateTexts.t(locale, "plugins"), style = MaterialTheme.typography.titleSmall)
                if (!current.indexAvailable) {
                    Text(
                        text = UpdateTexts.t(locale, "plugins.index_missing"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (current.plugins.isEmpty()) {
                    Text(
                        text = UpdateTexts.t(locale, "plugins.empty"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    current.plugins.forEach { state ->
                        when (state) {
                            is PluginState.UpToDate -> PluginRow(
                                id = state.installed.id,
                                localVersion = state.installed.version,
                                remoteVersion = state.remote.version,
                                action = {
                                    Text(
                                        text = UpdateTexts.t(locale, "plugin.up_to_date"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            is PluginState.UpdateAvailable -> PluginRow(
                                id = state.remote.id,
                                localVersion = state.installed?.version,
                                remoteVersion = state.remote.version,
                                action = {
                                    OutlinedButton(
                                        onClick = { install(state.remote, current.latestTag) },
                                        enabled = busyPlugin == null,
                                    ) {
                                        Text(
                                            UpdateTexts.t(
                                                locale,
                                                if (state.installed == null) "plugin.install" else "plugin.update",
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                Text(
                    text = UpdateTexts.t(locale, "builtin.note"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    private companion object {
        const val PAGE_ID: String = "dev.mmcl.page.update"
    }
}

@Composable
private fun PluginRow(
    id: String,
    localVersion: String?,
    remoteVersion: String,
    action: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(id, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${localVersion ?: "—"} → $remoteVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action()
    }
}
