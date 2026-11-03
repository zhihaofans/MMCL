package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import dev.mmcl.app.NavigationIntent
import dev.mmcl.core.download.DownloadTask
import dev.mmcl.core.download.DownloadTaskState
import dev.mmcl.core.download.DownloadsService
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.core.version.NoVersionSourceException
import dev.mmcl.core.version.VersionManifest
import dev.mmcl.core.version.VersionManifestEntry
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.core.version.VersionSupport
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import kotlinx.coroutines.launch

/**
 * 下载页：版本清单 + 安装/修复共享文件。
 *
 * - 真正的下载任务跑在 [DownloadsService] 里，切页不会取消；
 * - 无源时直接引导去设置页装来源插件；
 * - 安装完成后可按开关自动创建实例（避免"装完游戏页没有实例"）。
 */
class DownloadsPage(
    private val manifests: VersionManifestService? = null,
    private val downloads: DownloadsService? = null,
    private val instanceService: InstanceService? = null,
    private val intent: NavigationIntent? = null,
    private val autoLoad: Boolean = false,
    private val pluginFolder: PluginFolder? = null,
) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "下载"
    override val titleKey: String = "page.downloads.title"
    override val icon: UiIcon = UiIcon.DOWNLOAD
    override val group: UiGroup = UiGroup.MAIN
    override val order: Int = 10

    @Composable
    override fun Content(navigator: AppNavigator) {
        var state by remember { mutableStateOf<ManifestUiState>(ManifestUiState.Idle) }
        var focusedVersion by remember { mutableStateOf<String?>(null) }
        var autoCreateInstance by remember { mutableStateOf(true) }
        var filter by remember { mutableStateOf(VersionFilter.RELEASE) }
        var autoLoadTriggered by remember { mutableStateOf(false) }
        val handledCompletions = remember { mutableSetOf<String>() }
        val scope = rememberCoroutineScope()
        val taskFlow = remember(downloads) { downloads?.tasks }
        val tasks = if (taskFlow != null) taskFlow.collectAsState().value else emptyList()
        val hasSource = manifests?.hasSource() == true

        fun refresh(force: Boolean) {
            val service = manifests ?: return
            state = ManifestUiState.Loading
            scope.launch {
                state =
                    try {
                        ManifestUiState.Loaded(service.load(forceRefresh = force))
                    } catch (t: NoVersionSourceException) {
                        ManifestUiState.Failed("未安装下载源插件：把下载源插件放进 <数据目录>/plugins/ 后重启")
                    } catch (t: Throwable) {
                        ManifestUiState.Failed(t.message ?: t::class.simpleName ?: "未知错误")
                    }
            }
        }

        LaunchedEffect(Unit) {
            focusedVersion = intent?.consumeDownloadVersion()
            if (autoLoad && !autoLoadTriggered && hasSource) {
                autoLoadTriggered = true
                refresh(force = false)
            }
        }

        // 安装完成后自动建实例（同版本只建一次）
        LaunchedEffect(tasks) {
            tasks.filter { it.state == DownloadTaskState.COMPLETED }.forEach { task ->
                if (!autoCreateInstance) return@forEach
                if (!handledCompletions.add(task.versionId)) return@forEach
                val service = instanceService ?: return@forEach
                if (service.list().any { it.versionId == task.versionId }) return@forEach
                runCatching { service.create(task.versionId) }
            }
        }

        PageScaffold(
            title = "下载",
            subtitle = "版本与共享文件",
            actions = {
                if (hasSource) {
                    OutlinedButton(
                        onClick = { refresh(force = true) },
                        enabled = state != ManifestUiState.Loading,
                    ) {
                        Text(if (state == ManifestUiState.Loading) "获取中…" else "刷新版本清单")
                    }
                }
            },
        ) {
            when (val current = state) {
                ManifestUiState.Idle ->
                    if (hasSource) {
                        EmptyState(
                            icon = UiIcon.DOWNLOAD,
                            text = "还没有载入版本清单",
                            hint = "点右上角「刷新版本清单」，选择版本安装",
                        )
                    } else {
                        NoSourceGuide(
                            folder = pluginFolder,
                            modifier = Modifier.padding(MmclSpacing.m),
                        )
                    }

                ManifestUiState.Loading ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(MmclSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(MmclSpacing.xxl))
                    }

                is ManifestUiState.Failed -> Column {
                    Text(
                        text = "版本清单获取失败：${current.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { refresh(force = true) }) { Text("重试") }
                }

                is ManifestUiState.Loaded -> {
                    val manifest = current.manifest
                    val supportedSeries = VersionSupport.latestSeries(manifest)
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
                    ) {
                        focusedVersion?.let { version ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                    .padding(MmclSpacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "来自游戏页：先安装或修复 $version",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { focusedVersion = null }) { Text("知道了") }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VersionFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(option.label) },
                                )
                                Spacer(modifier = Modifier.size(MmclSpacing.xs))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "只支持最新正式版系列（$supportedSeries）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autoCreateInstance, onCheckedChange = { autoCreateInstance = it })
                            Text("安装完成后自动创建实例", style = MaterialTheme.typography.bodySmall)
                        }

                        val visibleVersions =
                            manifest.versions
                                .filter { entry ->
                                    when (filter) {
                                        VersionFilter.RELEASE -> entry.type == "release"
                                        VersionFilter.SNAPSHOT -> entry.type == "snapshot"
                                        VersionFilter.ALL -> true
                                    }
                                }
                                .take(MAX_ROWS)
                        if (visibleVersions.isEmpty()) {
                            Text(
                                text = "这个分类下没有版本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        visibleVersions.forEach { entry ->
                            VersionRow(
                                entry = entry,
                                supported = VersionSupport.isSupported(entry, supportedSeries),
                                task = tasks.firstOrNull { it.versionId == entry.id },
                                focused = entry.id == focusedVersion,
                                onInstall = { downloads?.enqueue(entry) },
                                onCancel = { downloads?.cancel(entry.id) },
                            )
                        }

                        HorizontalDivider()
                        ManifestSummary(manifest)
                    }
                }
            }
        }
    }

    private enum class VersionFilter(val label: String) {
        RELEASE("正式版"),
        SNAPSHOT("快照"),
        ALL("全部"),
    }

    companion object {
        private const val MAX_ROWS = 30

        /** 页面 id，供其他页面跳转。 */
        const val PAGE_ID: String = "dev.mmcl.page.downloads"
    }
}

@Composable
private fun VersionRow(
    entry: VersionManifestEntry,
    supported: Boolean,
    task: DownloadTask?,
    focused: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val active = task?.state?.isActive == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                },
            )
            .padding(MmclSpacing.s),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.id,
                style = MaterialTheme.typography.bodyLarge,
                color = if (supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.size(MmclSpacing.s))
            Text(
                text = if (supported) entry.type else "${entry.type} · 不受支持",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (active) {
                TextButton(onClick = onCancel) { Text("取消") }
            } else {
                Button(onClick = onInstall, enabled = supported) {
                    Text(if (task?.state == DownloadTaskState.COMPLETED) "修复" else "安装")
                }
            }
        }

        task?.let { TaskLine(it) }
    }
}

@Composable
private fun TaskLine(task: DownloadTask) {
    when (task.state) {
        DownloadTaskState.QUEUED -> Text("排队中…", style = MaterialTheme.typography.labelSmall)
        DownloadTaskState.PLANNING -> Text("正在规划文件…", style = MaterialTheme.typography.labelSmall)
        DownloadTaskState.DOWNLOADING -> {
            val progress = task.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = {
                        if (progress.total <= 0) 0f else progress.completed.toFloat() / progress.total
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${if (progress.phase == "assets") "资源" else "文件"} ${progress.completed}/${progress.total}" +
                        if (progress.current.isNotBlank()) "：${progress.current}" else "",
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                Text("下载中…", style = MaterialTheme.typography.labelSmall)
            }
        }
        DownloadTaskState.COMPLETED -> {
            val report = task.result?.report
            Text(
                text = "完成：下载 ${report?.downloaded ?: 0}，跳过 ${report?.skipped ?: 0}，失败 ${report?.failures?.size ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DownloadTaskState.FAILED ->
            Text(
                text = "失败：${task.error ?: "未知错误"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        DownloadTaskState.CANCELLED ->
            Text("已取消", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

/** 版本清单摘要：最新正式版/快照 + 支持范围 + 前几个版本。 */
@Composable
internal fun ManifestSummary(manifest: VersionManifest) {
    val supportedSeries = VersionSupport.latestSeries(manifest)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(MmclSpacing.l),
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        Text(text = "Mojang 版本清单", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "只支持最新正式版系列（$supportedSeries）；其余仅展示，不可下载或启动",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ManifestRow("最新正式版", manifest.latest.release)
        ManifestRow("最新快照", manifest.latest.snapshot)
        ManifestRow("版本总数", manifest.versions.size.toString())
        HorizontalDivider(modifier = Modifier.padding(vertical = MmclSpacing.xs))
        manifest.versions.take(6).forEach { entry ->
            val supported = VersionSupport.isSupported(entry, supportedSeries)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.id,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (supported) "${entry.type} · 支持" else "${entry.type} · 不受支持",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (supported) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
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
