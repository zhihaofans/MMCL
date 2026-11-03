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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.app.NavigationIntent
import dev.mmcl.core.instance.Instance
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.core.java.JavaRuntimeDetector
import dev.mmcl.core.download.DownloadsService
import dev.mmcl.core.java.JavaRuntimeInstaller
import dev.mmcl.core.java.JavaRuntimeSources
import dev.mmcl.core.launch.LaunchRequest
import dev.mmcl.core.launch.LaunchSession
import dev.mmcl.core.launch.MinecraftLauncher
import dev.mmcl.core.version.NoVersionSourceException
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.core.version.VersionManifestEntry
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.ui.kit.EmptyState
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
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
 * 游戏页：实例列表 + 启动 + 日志。
 *
 * 共享文件（libraries / assets / versions）的安装与修复在「下载」页；
 * 这里只显示缺文件状态，并给一个跳过去的入口。
 */
class InstancesPage(
    private val manifests: VersionManifestService? = null,
    private val instanceService: InstanceService? = null,
    private val detailService: VersionDetailService? = null,
    private val javaDetector: JavaRuntimeDetector? = null,
    private val javaInstaller: JavaRuntimeInstaller? = null,
    private val javaSources: JavaRuntimeSources? = null,
    private val launcher: MinecraftLauncher? = null,
    private val launchContext: LaunchContext? = null,
    private val authProviders: List<AuthProvider> = emptyList(),
    private val intent: NavigationIntent? = null,
    private val autoLoad: Boolean = false,
    private val pluginFolder: PluginFolder? = null,
    private val downloads: DownloadsService? = null,
) : UiPage {
    override val id: String = INSTANCES_PAGE_ID
    override val title: String = "游戏"
    override val titleKey: String = "page.instances.title"
    override val icon: UiIcon = UiIcon.GAME
    override val group: UiGroup = UiGroup.MAIN
    override val order: Int = 0

    companion object {
        /** 页面 id，供其它页面跳转。 */
        const val PAGE_ID: String = INSTANCES_PAGE_ID
    }

    @Composable
    override fun Content(navigator: AppNavigator) {
        var state by remember { mutableStateOf<ManifestUiState>(ManifestUiState.Idle) }
        var launchStatus by remember { mutableStateOf<String?>(null) }
        var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }
        var pendingDelete by remember { mutableStateOf<Instance?>(null) }
        var editingParams by remember { mutableStateOf<Instance?>(null) }
        var runningSession by remember { mutableStateOf<LaunchSession?>(null) }
        var runningInstanceId by remember { mutableStateOf<String?>(null) }
        var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
        var exitedCode by remember { mutableStateOf<Int?>(null) }
        var logOwnerName by remember { mutableStateOf<String?>(null) }
        var missingJava by remember { mutableStateOf<Int?>(null) }
        var javaMessage by remember { mutableStateOf<String?>(null) }
        var javaBusy by remember { mutableStateOf(false) }
        var pendingJavaInstance by remember { mutableStateOf<Instance?>(null) }
        var autoLoadTriggered by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val clipboard = LocalClipboardManager.current
        val hasSource = manifests?.hasSource() == true

        fun refreshInstances() {
            instances = instanceService?.list().orEmpty()
        }

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

        fun clientJar(versionId: String): Path? =
            launchContext?.sharedRoot?.resolve("versions")?.resolve(versionId)?.resolve("$versionId.jar")

        fun hasFiles(instance: Instance): Boolean = clientJar(instance.versionId)?.let { Files.isRegularFile(it) } ?: true

        fun openDownloads(versionId: String? = null) {
            if (versionId != null) intent?.requestDownload(versionId)
            navigator.navigate(DownloadsPage.PAGE_ID)
        }

        /** 校验并修复：重新规划文件后由下载器跳过完好文件、补下缺失与损坏的。 */
        fun repairInstance(instance: Instance) {
            val queue = downloads
            if (queue == null) {
                launchStatus = "下载队列未接线，无法校验"
                return
            }
            val manifest = (state as? ManifestUiState.Loaded)?.manifest
            val entry = manifest?.find(instance.versionId) ?: VersionManifestEntry(instance.versionId, "release", "")
            queue.enqueue(entry, forceRefresh = true)
            launchStatus = "已加入校验/修复队列：${instance.versionId}，进度见「下载」页"
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
            if (!hasFiles(instance)) {
                launchStatus = "共享文件缺失：去「下载」页安装或修复 ${instance.versionId} 后再启动"
                return
            }
            val provider = authProviders.minWithOrNull(compareBy({ it.priority }, { it.id }))
            if (provider == null) {
                launchStatus = "缺少账户插件：安装 auth-offline（内置）后重启启动器"
                return
            }
            // 没有清单源时退回空 URL：详情服务会优先读本地缓存
            val entry = manifest?.find(instance.versionId) ?: VersionManifestEntry(instance.versionId, "release", "")
            launchStatus = "正在启动 ${instance.name}…"
            scope.launch {
                try {
                    val detail = details.load(entry)
                    val required = detail.javaVersion?.majorVersion ?: 0
                    val runtime = detector.detect(required)
                    if (runtime == null) {
                        missingJava = required
                        pendingJavaInstance = instance
                        launchStatus = "缺少 Java ≥$required，无法启动 ${instance.name}；可用下面的按钮下载"
                        return@launch
                    }
                    val auth = provider.createSession(context.playerName)
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
                                auth = auth,
                                maxMemoryMb = instance.memoryMb ?: DEFAULT_MEMORY_MB,
                                extraJvmArgs = splitJvmArgs(instance.extraJvmArgs),
                                environment = parseEnvVars(instance.envVars),
                            ),
                        )
                    runCatching { service.markLaunched(instance.id) }
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
            intent?.consumeLaunchInstance()?.let { id -> instances.firstOrNull { it.id == id }?.let { launchInstance(it) } }
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

        fun downloadJava(required: Int) {
            val installer = javaInstaller
            val context = launchContext
            val sources = javaSources
            if (installer == null || context == null || sources == null) {
                javaMessage = "没有可用的运行时来源插件：去「设置」安装后重启"
                return
            }
            javaBusy = true
            javaMessage = "正在解析 Java $required…"
            scope.launch {
                try {
                    // 镜像站常缺某个平台/版本；安装期失败（例如错平台的包）也会自动换下一个来源
                    val installed =
                        sources.installFirst(required, context.os, context.arch, installer) { bytes ->
                            javaMessage = "已下载 ${bytes / 1024 / 1024} MB…"
                        }
                            ?: error(
                                if (sources.hasProvider()) {
                                    "没有来源支持 ${context.os} / ${context.arch}"
                                } else {
                                    "没有可用的运行时来源插件：去「设置」安装后重启"
                                },
                            )
                    val (provider, home) = installed
                    javaMessage = "${provider.displayName} 的 Java $required 已安装"
                    missingJava = null
                    javaBusy = false
                    pendingJavaInstance?.let { launchInstance(it) }
                    pendingJavaInstance = null
                } catch (t: Throwable) {
                    javaBusy = false
                    javaMessage = "下载 Java 失败：${t.message ?: t::class.simpleName}"
                }
            }
        }

        PageScaffold(
            title = "游戏",
            subtitle = "实例、启动与日志",
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
                    Button(onClick = { openDownloads() }) { Text("安装新版本") }
                }
            },
        ) {
            val account = authProviders.minWithOrNull(compareBy({ it.priority }, { it.id }))
            val missing = instances.filter { !hasFiles(it) }
            Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.m)) {
                Text(
                    text = if (account == null) {
                        "账户：未安装认证插件（游戏无法启动）"
                    } else {
                        "账户：${account.displayName} · ${launchContext?.playerName ?: "Player"}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (account == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!hasSource) {
                    NoSourceGuide(folder = pluginFolder)
                    Text(
                        text = "没有下载源时仍可启动本机已装好的实例。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (missing.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                            .padding(MmclSpacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "有 ${missing.size} 个实例缺少共享文件，启动会被拦下",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { openDownloads(missing.first().versionId) }) { Text("去下载") }
                    }
                }

                if (instances.isEmpty()) {
                    EmptyState(
                        icon = UiIcon.GAME,
                        text = "还没有实例",
                        hint = if (hasSource) {
                            "点右上角「安装新版本」，在下载页选版本安装"
                        } else {
                            "先安装版本源插件，再安装游戏版本"
                        },
                    )
                } else {
                    instances.forEach { instance ->
                        InstanceCard(
                            instance = instance,
                            missingFiles = !hasFiles(instance),
                            running = runningInstanceId == instance.id,
                            onLaunch = { launchInstance(instance) },
                            onRepair = { repairInstance(instance) },
                            onEditParams = { editingParams = instance },
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
                        onCopy = {
                            clipboard.setText(AnnotatedString(logLines.joinToString("\n")))
                            launchStatus = "日志已复制到剪贴板"
                        },
                        onExport = {
                            launchStatus =
                                exportLog(logLines.joinToString("\n")).fold(
                                    onSuccess = { "日志已导出到 $it" },
                                    onFailure = { "导出失败：${it.message ?: it::class.simpleName}" },
                                )
                        },
                        onStop = {
                            runningSession?.stop()
                            launchStatus = "已请求停止游戏"
                        },
                        onForceStop = {
                            runningSession?.forceStop()
                            launchStatus = "已强制结束游戏进程"
                        },
                    )
                }

                launchStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                missingJava?.let { required ->
                    val provider = javaSources?.current()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                            .padding(MmclSpacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (provider == null) {
                                "缺少 Java ≥$required：去「设置」安装运行时来源插件"
                            } else {
                                "缺少 Java ≥$required（来源：${provider.displayName}）"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { downloadJava(required) }, enabled = provider != null && !javaBusy) {
                            Text(if (javaBusy) "下载中…" else "下载 Java $required")
                        }
                    }
                }
                javaMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (val current = state) {
                    ManifestUiState.Loading ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(MmclSpacing.s))
                            Text("正在获取版本清单…", style = MaterialTheme.typography.labelSmall)
                        }
                    is ManifestUiState.Failed ->
                        Text(
                            text = "版本清单获取失败：${current.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    else -> Unit
                }
            }
        }

        editingParams?.let { target ->
            LaunchParamsDialog(
                instance = target,
                onDismiss = { editingParams = null },
                onSave = { memory, jvmArgs, envVars ->
                    val ok = instanceService?.updateLaunchSettings(target.id, memory, jvmArgs, envVars) == true
                    editingParams = null
                    refreshInstances()
                    launchStatus =
                        if (ok) {
                            "已保存 ${target.name} 的启动参数"
                        } else {
                            "保存启动参数失败：实例可能已被删除"
                        }
                },
            )
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
    missingFiles: Boolean = false,
    running: Boolean,
    onLaunch: () -> Unit,
    onRepair: (() -> Unit)? = null,
    onEditParams: (() -> Unit)? = null,
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
            if (missingFiles) {
                Text(
                    text = "缺共享文件 · 去下载页安装或修复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Button(onClick = onLaunch, enabled = !running) { Text(if (running) "运行中" else "启动") }
        onRepair?.let { TextButton(onClick = it, enabled = !running) { Text("校验/修复") } }
        onEditParams?.let { TextButton(onClick = it, enabled = !running) { Text("参数") } }
        TextButton(onClick = onDelete, enabled = !running) { Text("删除") }
    }
}

/** 实例页的固定页面 id（首页等其它页面跳转用）。 */
const val INSTANCES_PAGE_ID: String = "dev.mmcl.page.instances"

/** 用系统"另存为"对话框导出日志；取消或不选路径时返回失败原因。 */
private fun exportLog(text: String): Result<Path> =
    runCatching {
        val dialog = FileDialog(null as Frame?, "导出游戏日志", FileDialog.SAVE)
        dialog.file = "latest.log"
        dialog.isVisible = true
        val directory = dialog.directory ?: error("没有选择保存位置")
        val name = dialog.file ?: error("没有输入文件名")
        val target = Path.of(directory, name)
        Files.writeString(target, text)
        target
    }

/** 默认堆上限（MB），与 `LaunchRequest` 的缺省值保持一致。 */
private const val DEFAULT_MEMORY_MB = 2048

/** 把空格分隔的 JVM 参数拆成列表。 */
internal fun splitJvmArgs(raw: String): List<String> =
    raw.split(Regex("\\s+")).filter { it.isNotBlank() }

/** 把 `KEY=VALUE` 逐行解析成环境变量；忽略空行、注释与没有 `=` 的行。 */
internal fun parseEnvVars(raw: String): Map<String, String> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
        .filterKeys { it.isNotEmpty() }

/** 实例级启动参数编辑：内存上限、额外 JVM 参数、额外环境变量。 */
@Composable
private fun LaunchParamsDialog(
    instance: Instance,
    onDismiss: () -> Unit,
    onSave: (memoryMb: Int?, extraJvmArgs: String, envVars: String) -> Unit,
) {
    var memoryText by remember(instance) { mutableStateOf(instance.memoryMb?.toString() ?: "") }
    var jvmArgs by remember(instance) { mutableStateOf(instance.extraJvmArgs) }
    var envVars by remember(instance) { mutableStateOf(instance.envVars) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("启动参数 · ${instance.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.s)) {
                OutlinedTextField(
                    value = memoryText,
                    onValueChange = { memoryText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("内存上限（MB，留空用默认 $DEFAULT_MEMORY_MB）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = jvmArgs,
                    onValueChange = { jvmArgs = it },
                    label = { Text("额外 JVM 参数（空格分隔）") },
                    placeholder = { Text("-XX:+UseG1GC") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = envVars,
                    onValueChange = { envVars = it },
                    label = { Text("环境变量（每行 KEY=VALUE）") },
                    placeholder = { Text("JAVA_TOOL_OPTIONS=-Dfoo=bar") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(memoryText.toIntOrNull(), jvmArgs, envVars) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
