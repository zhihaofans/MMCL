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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.core.download.DownloadProgress
import dev.mmcl.core.download.DownloadReport
import dev.mmcl.core.download.GameInstaller
import dev.mmcl.core.download.InstallPlan
import dev.mmcl.core.download.InstallResult
import dev.mmcl.core.instance.Instance
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.core.java.JavaRuntimeDetector
import dev.mmcl.core.java.JavaRuntimeSource
import dev.mmcl.core.launch.LaunchRequest
import dev.mmcl.core.launch.LaunchSession
import dev.mmcl.core.launch.MinecraftLauncher
import dev.mmcl.core.version.NoVersionSourceException
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.core.version.VersionManifest
import dev.mmcl.core.version.VersionManifestEntry
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.core.version.VersionSupport
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 启动所需的环境信息，由 `Main` 组装。
 *
 * @property sharedRoot 共享根（`<dataDir>`）：libraries / assets / versions
 * @property playerName 离线占位玩家名（等 `auth-*` 插件落地后替换）
 */
data class LaunchContext(
    val sharedRoot: Path,
    val launcherVersion: String,
    val os: OperatingSystem,
    val arch: Architecture,
    val playerName: String = "Player",
)

/**
 * 游戏页：实例列表 + 共享文件准备。
 *
 * 结构：
 * - 实例卡片（启动 / 删除）；实例拥有独立游戏目录（存档、配置、mods）；
 * - 共享文件：libraries / assets / versions 只下一份，所有实例复用；
 * - 版本清单里只允许最新正式版系列，其余标"不受支持"。
 */
class InstancesPage(
    private val manifests: VersionManifestService? = null,
    private val installer: GameInstaller? = null,
    private val instanceService: InstanceService? = null,
    private val detailService: VersionDetailService? = null,
    private val javaDetector: JavaRuntimeDetector? = null,
    private val launcher: MinecraftLauncher? = null,
    private val launchContext: LaunchContext? = null,
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
        var prep by remember { mutableStateOf<PrepUiState>(PrepUiState.Idle) }
        var javaStatus by remember { mutableStateOf<String?>(null) }
        var launchStatus by remember { mutableStateOf<String?>(null) }
        var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }
        var pendingDelete by remember { mutableStateOf<Instance?>(null) }
        var runningSession by remember { mutableStateOf<LaunchSession?>(null) }
        var runningInstanceId by remember { mutableStateOf<String?>(null) }
        var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
        var exitedCode by remember { mutableStateOf<Int?>(null) }
        var logOwnerName by remember { mutableStateOf<String?>(null) }
        var autoLoadTriggered by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val hasSource = manifests?.hasSource() == true

        fun refreshInstances() {
            instances = instanceService?.list().orEmpty()
        }

        fun refresh(force: Boolean) {
            val service = manifests ?: return
            prep = PrepUiState.Idle
            javaStatus = null
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

        fun describeJava(detail: dev.mmcl.core.version.VersionDetail): String {
            val required = detail.javaVersion?.majorVersion ?: 0
            if (required <= 0) return "该版本未声明 Java 要求"
            val detector = javaDetector ?: return "需要 Java ≥$required（检测器未接入）"
            val runtime = detector.detect(required)
            return if (runtime == null) {
                "缺少 Java ≥$required：安装 JDK，或在设置里填写 java.home"
            } else {
                "Java ${runtime.majorVersion}（${javaSourceLabel(runtime.source)}）"
            }
        }

        fun checkFiles(entry: VersionManifestEntry) {
            val inst = installer ?: return
            prep = PrepUiState.Planning
            scope.launch {
                try {
                    val plan = inst.plan(entry, forceRefresh = true)
                    javaStatus = describeJava(plan.detail)
                    prep = PrepUiState.Ready(plan)
                } catch (t: Throwable) {
                    prep = PrepUiState.Failed(t.message ?: "检查文件失败")
                }
            }
        }

        fun downloadFiles(entry: VersionManifestEntry) {
            val inst = installer ?: return
            prep = PrepUiState.Downloading(null)
            scope.launch {
                try {
                    val result =
                        inst.install(entry, forceRefresh = true) { progress ->
                            prep = PrepUiState.Downloading(progress)
                        }
                    javaStatus = describeJava(result.plan.detail)
                    prep = PrepUiState.Done(result)
                } catch (t: Throwable) {
                    prep = PrepUiState.Failed(t.message ?: "下载失败")
                }
            }
        }

        fun createInstance(versionId: String) {
            val service = instanceService ?: return
            runCatching { service.create(versionId) }
                .onSuccess {
                    refreshInstances()
                    launchStatus = "已创建实例 ${it.name}"
                }
                .onFailure { launchStatus = "新建实例失败：${it.message ?: it::class.simpleName}" }
        }

        fun launchInstance(instance: Instance) {
            val details = detailService
            val detector = javaDetector
            val process = launcher
            val context = launchContext
            val service = instanceService
            val manifest = (state as? ManifestUiState.Loaded)?.manifest
            if (details == null || detector == null || process == null || context == null || service == null) {
                launchStatus = "启动器未接线，无法启动"
                return
            }
            val entry = manifest?.find(instance.versionId)
            if (entry == null) {
                launchStatus = "版本清单里找不到 ${instance.versionId}，先刷新版本清单"
                return
            }
            val clientJar = context.sharedRoot.resolve("versions").resolve(instance.versionId).resolve("${instance.versionId}.jar")
            if (!java.nio.file.Files.isRegularFile(clientJar)) {
                launchStatus = "共享文件缺失：先点下方的「检查文件 / 下载游戏文件」再启动"
                return
            }
            runCatching { java.nio.file.Files.createDirectories(service.directoryOf(instance)) }
            launchStatus = "正在启动 ${instance.name}…"
            scope.launch {
                try {
                    val detail = details.load(entry)
                    val required = detail.javaVersion?.majorVersion ?: 0
                    val runtime = detector.detect(required)
                    if (runtime == null) {
                        launchStatus = "缺少 Java ≥$required，无法启动 ${instance.name}"
                        return@launch
                    }
                    val session =
                        process.launch(
                            LaunchRequest(
                                detail = detail,
                                sharedRoot = context.sharedRoot,
                                instanceDir = service.directoryOf(instance),
                                javaExecutable = runtime.executable,
                                launcherVersion = context.launcherVersion,
                                os = context.os,
                                arch = context.arch,
                                playerName = context.playerName,
                            ),
                        )
                    logLines = session.snapshot()
                    exitedCode = null
                    logOwnerName = instance.name
                    runningSession = session
                    runningInstanceId = instance.id
                    launchStatus = "${instance.name} 已启动（pid ${session.pid}）"
                } catch (t: Throwable) {
                    launchStatus = "启动失败：${t.message ?: t::class.simpleName}"
                    runningSession = null
                    runningInstanceId = null
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshInstances()
            if (autoLoad && !autoLoadTriggered && hasSource) {
                autoLoadTriggered = true
                refresh(force = false)
            }
        }

        LaunchedEffect(runningSession) {
            val session = runningSession ?: return@LaunchedEffect
            logLines = session.snapshot()
            launch {
                session.output.collect { line ->
                    logLines = (logLines + line).takeLast(LaunchSession.MAX_LINES)
                }
            }
            while (session.isRunning) delay(500)
            logLines = session.snapshot()
            exitedCode = session.process.exitValue()
            launchStatus = "游戏已退出（code ${exitedCode}）"
            runningSession = null
            runningInstanceId = null
        }

        val latest = (state as? ManifestUiState.Loaded)?.manifest?.latestRelease()

        PageScaffold(
            title = "游戏",
            subtitle = "实例与共享文件",
            actions = {
                if (hasSource) {
                    OutlinedButton(
                        onClick = { refresh(force = true) },
                        enabled = state != ManifestUiState.Loading,
                    ) {
                        Text(if (state == ManifestUiState.Loading) "获取中…" else "刷新版本清单")
                    }
                }
                if (instanceService != null) {
                    Button(onClick = { latest?.let { createInstance(it.id) } }, enabled = latest != null) {
                        Text("新建实例")
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
                            hint = "点右上角「刷新版本清单」，再「新建实例」",
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

                is ManifestUiState.Loaded -> {
                    Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.m)) {
                        if (instances.isEmpty()) {
                            EmptyState(
                                icon = UiIcon.GAME,
                                text = "还没有实例",
                                hint = "点右上角「新建实例」，会用最新正式版 ${current.manifest.latest.release} 创建",
                            )
                        } else {
                            instances.forEach { instance ->
                                InstanceCard(
                                    instance = instance,
                                    running = runningInstanceId == instance.id,
                                    onLaunch = { launchInstance(instance) },
                                    onDelete = { pendingDelete = instance },
                                )
                            }
                        }

                        if (logOwnerName != null && (logLines.isNotEmpty() || runningSession != null || exitedCode != null)) {
                            GameLogPanel(
                                title = "游戏日志 · ${logOwnerName}",
                                lines = logLines,
                                running = runningSession != null,
                                exitCode = exitedCode,
                                onClear = { logLines = emptyList() },
                            )
                        }

                        launchStatus?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        HorizontalDivider()
                        Text(
                            text = "共享文件（libraries / assets / versions，所有实例复用）",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        latest?.let { entry ->
                            GamePrepCard(
                                versionId = entry.id,
                                plan = (prep as? PrepUiState.Ready)?.plan,
                                progress = (prep as? PrepUiState.Downloading)?.progress,
                                report = (prep as? PrepUiState.Done)?.result?.report,
                                failure = (prep as? PrepUiState.Failed)?.message,
                                javaStatus = javaStatus,
                                busy = prep is PrepUiState.Planning || prep is PrepUiState.Downloading,
                                onCheck = { checkFiles(entry) },
                                onDownload = { downloadFiles(entry) },
                            )
                        }
                        ManifestSummary(current.manifest)
                    }
                }
            }
        }

        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除实例") },
                text = { Text("会连同「${target.name}」的存档、配置与 mods 一起删除，且不可恢复。共享文件不受影响。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            instanceService?.delete(target.id)
                            refreshInstances()
                            pendingDelete = null
                            launchStatus = "已删除实例 ${target.name}"
                        },
                    ) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            )
        }
    }
}

/** 实例卡片：名称、版本、目录、启动与删除。 */
@Composable
internal fun InstanceCard(
    instance: Instance,
    running: Boolean,
    onLaunch: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(MmclSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = instance.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "版本 ${instance.versionId} · instances/${instance.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onLaunch, enabled = !running) { Text(if (running) "运行中" else "启动") }
        TextButton(onClick = onDelete, enabled = !running) { Text("删除") }
    }
}

/** 版本清单摘要卡片：最新正式版/快照 + 支持范围 + 前几个版本。 */
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

/** 运行时来源的中文说明。 */
private fun javaSourceLabel(source: JavaRuntimeSource): String =
    when (source) {
        JavaRuntimeSource.CONFIGURED -> "设置指定"
        JavaRuntimeSource.BUNDLED -> "自带运行时"
        JavaRuntimeSource.JAVA_HOME -> "JAVA_HOME"
        JavaRuntimeSource.PATH -> "PATH"
        JavaRuntimeSource.SYSTEM_MACOS -> "系统 JDK"
    }

private sealed interface ManifestUiState {
    data object Idle : ManifestUiState
    data object Loading : ManifestUiState
    data class Loaded(val manifest: VersionManifest) : ManifestUiState
    data class Failed(val message: String) : ManifestUiState
}

private sealed interface PrepUiState {
    data object Idle : PrepUiState
    data object Planning : PrepUiState
    data class Ready(val plan: InstallPlan) : PrepUiState
    data class Downloading(val progress: DownloadProgress?) : PrepUiState
    data class Done(val result: InstallResult) : PrepUiState
    data class Failed(val message: String) : PrepUiState
}
