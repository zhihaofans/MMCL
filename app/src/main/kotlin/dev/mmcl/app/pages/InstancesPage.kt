package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.core.version.NoVersionSourceException
import dev.mmcl.core.version.VersionManifest
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import kotlinx.coroutines.launch

/**
 * 游戏页：0.1.x 先接入版本清单，证明启动链第一步（拿清单）通了；实例列表等执行链落地后再填。
 *
 * @param manifests 版本清单服务；为 `null` 时不显示刷新入口（渲染测试用）
 * @param autoLoad 首次进入是否自动拉取（有缓存会直接命中缓存）
 */
class InstancesPage(
    private val manifests: VersionManifestService? = null,
    private val autoLoad: Boolean = false,
) : UiPage {
    override val id: String = "dev.mmcl.page.instances"
    override val title: String = "游戏"
    override val icon: UiIcon = UiIcon.GAME
    override val group: UiGroup = UiGroup.MAIN
    override val order: Int = 0

    @Composable
    override fun Content(navigator: AppNavigator) {
        var state by remember { mutableStateOf<ManifestUiState>(ManifestUiState.Idle) }
        // 自动加载只触发一次：LaunchedEffect 在重组时可能重跑，用标记兜底避免重复请求
        var autoLoadTriggered by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun refresh(force: Boolean) {
            val service = manifests ?: return
            state = ManifestUiState.Loading
            scope.launch {
                state =
                    try {
                        ManifestUiState.Loaded(service.load(forceRefresh = force))
                    } catch (t: NoVersionSourceException) {
                        ManifestUiState.Failed("未安装版本源插件：把 source-mojang 等插件放进 <数据目录>/plugins/ 后重启")
                    } catch (t: Throwable) {
                        ManifestUiState.Failed(t.message ?: t::class.simpleName ?: "未知错误")
                    }
            }
        }

        val hasSource = manifests?.hasSource() == true

        LaunchedEffect(Unit) {
            if (autoLoad && !autoLoadTriggered && hasSource) {
                autoLoadTriggered = true
                refresh(force = false)
            }
        }

        PageScaffold(
            title = "游戏",
            subtitle = "实例与版本",
            actions = {
                if (hasSource) {
                    Button(onClick = { refresh(force = true) }, enabled = state != ManifestUiState.Loading) {
                        Text(if (state == ManifestUiState.Loading) "获取中…" else "刷新版本清单")
                    }
                }
            },
        ) {
            when (val current = state) {
                ManifestUiState.Idle ->
                    if (hasSource) {
                        EmptyState(
                            icon = UiIcon.GAME,
                            text = "还没有实例",
                            hint = "点右上角「刷新版本清单」拉取版本列表；启动执行链还在开发中",
                        )
                    } else {
                        EmptyState(
                            icon = UiIcon.DOWNLOAD,
                            text = "没有可用的版本源",
                            hint = "本应用不内置任何下载地址：把 source-mojang 等版本源插件放进 <数据目录>/plugins/ 后重启",
                        )
                    }

                ManifestUiState.Loading ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(MmclSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(MmclSpacing.xxl))
                    }

                is ManifestUiState.Failed ->
                    Text(
                        text = "版本清单获取失败：${current.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                is ManifestUiState.Loaded -> ManifestSummary(current.manifest)
            }
        }
    }
}

/** 版本清单摘要卡片：最新正式版/快照 + 版本总数 + 前几个版本。 */
@Composable
internal fun ManifestSummary(manifest: VersionManifest) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(MmclSpacing.l),
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        Text(text = "Mojang 版本清单", style = MaterialTheme.typography.titleSmall)
        ManifestRow("最新正式版", manifest.latest.release)
        ManifestRow("最新快照", manifest.latest.snapshot)
        ManifestRow("版本总数", manifest.versions.size.toString())
        HorizontalDivider(modifier = Modifier.padding(vertical = MmclSpacing.xs))
        manifest.versions.take(6).forEach { entry ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = entry.id, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = entry.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ManifestRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private sealed interface ManifestUiState {
    data object Idle : ManifestUiState
    data object Loading : ManifestUiState
    data class Loaded(val manifest: VersionManifest) : ManifestUiState
    data class Failed(val message: String) : ManifestUiState
}
