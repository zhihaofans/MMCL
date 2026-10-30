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
import dev.mmcl.core.download.DownloadProgress
import dev.mmcl.core.download.DownloadReport
import dev.mmcl.core.download.InstallResult
import dev.mmcl.core.download.GameInstaller
import dev.mmcl.core.download.InstallPlan
import dev.mmcl.core.java.JavaRuntimeDetector
import dev.mmcl.core.launch.LaunchRequest
import dev.mmcl.core.launch.LaunchSession
import dev.mmcl.core.launch.MinecraftLauncher
import dev.mmcl.core.java.JavaRuntimeSource
import dev.mmcl.core.version.NoVersionSourceException
import dev.mmcl.core.version.VersionManifest
import dev.mmcl.core.version.VersionManifestEntry
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.core.version.VersionSupport
import java.nio.file.Path
import kotlinx.coroutines.delay
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import kotlinx.coroutines.launch

/**
 * 游戏页：版本清单 + 最新正式版的"检查文件 / 下载游戏文件"。
 *
 * 启动执行链还没落地，所以这里不提供"启动"按钮；下载完成只代表文件齐了。
 *
 * @param manifests 版本清单服务；为 `null` 时不显示刷新入口（渲染测试用）
 * @param installer 安装编排；为 `null` 时不显示准备卡片
 * @param autoLoad 首次进入是否自动拉取清单（有缓存会直接命中缓存）
 */
/**
 * 启动所需的环境信息，由 `Main` 组装。
 *
 * @property playerName 离线占位玩家名（等 `auth-*` 插件落地后替换）
 */
data class LaunchContext(
    val gameDir: Path,
    val launcherVersion: String,
    val os: dev.mmcl.api.OperatingSystem,
    val arch: dev.mmcl.api.Architecture,
    val playerName: String = "Player",
)

class InstancesPage(
    private val manifests: VersionManifestService? = null,
    private val installer: GameInstaller? = null,
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
        var runningSession by remember { mutableStateOf<LaunchSession?>(null) }
        // 自动加载只触发一次：LaunchedEffect 在重组时可能重跑，用标记兜底避免重复请求
        var autoLoadTriggered by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val hasSource = manifests?.hasSource() == true

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

        fun describeJava(plan: InstallPlan): String {
            val required = plan.detail.javaVersion?.majorVersion ?: 0
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
                    javaStatus = describeJava(plan)
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
                    javaStatus = describeJava(result.plan)
                    prep = PrepUiState.Done(result)
                } catch (t: Throwable) {
                    prep = PrepUiState.Failed(t.message ?: "下载失败")
                }
            }
        }

        fun startGame(plan: InstallPlan) {
            val detector = javaDetector
            val instance = launcher
            val context = launchContext
            if (detector == null || instance == null || context == null) {
                launchStatus = "启动器未接线，无法启动游戏"
                return
            }
            val required = plan.detail.javaVersion?.majorVersion ?: 0
            val runtime = detector.detect(required)
            if (runtime == null) {
                launchStatus = "缺少 Java ≥$required，无法启动"
                return
            }
            try {
                val session =
                    instance.launch(
                        LaunchRequest(
                            detail = plan.detail,
                            gameDir = context.gameDir,
                            javaExecutable = runtime.executable,
                            launcherVersion = context.launcherVersion,
                            os = context.os,
                            arch = context.arch,
                            playerName = context.playerName,
                        ),
                    )
                runningSession = session
                launchStatus = "已启动（pid ${session.pid}）"
            } catch (t: Throwable) {
                launchStatus = "启动失败：${t.message ?: t::class.simpleName}"
                runningSession = null
            }
        }

        LaunchedEffect(runningSession) {
            val session = runningSession ?: return@LaunchedEffect
            while (session.isRunning) delay(1_000)
            launchStatus = "游戏已退出（code ${session.process.exitValue()}）"
            runningSession = null
        }

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

                is ManifestUiState.Loaded -> {
                    val latest = current.manifest.latestRelease()
                    Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.m)) {
                        ManifestSummary(current.manifest)
                        if (installer != null && latest != null) {
                            GamePrepCard(
                                versionId = latest.id,
                                plan = (prep as? PrepUiState.Ready)?.plan,
                                progress = (prep as? PrepUiState.Downloading)?.progress,
                                report = (prep as? PrepUiState.Done)?.result?.report,
                                failure = (prep as? PrepUiState.Failed)?.message,
                                javaStatus = javaStatus,
                                onLaunch =
                                    (prep as? PrepUiState.Done)?.let { done ->
                                        { startGame(done.result.plan) }
                                    },
                                launchStatus = launchStatus,
                                running = runningSession != null,
                                onStop = { runningSession?.stop()?.also { launchStatus = "正在停止游戏…" } },
                                busy = prep is PrepUiState.Planning || prep is PrepUiState.Downloading,
                                onCheck = { checkFiles(latest) },
                                onDownload = { downloadFiles(latest) },
                            )
                        }
                    }
                }
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
        val supportedSeries = VersionSupport.latestSeries(manifest)
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
