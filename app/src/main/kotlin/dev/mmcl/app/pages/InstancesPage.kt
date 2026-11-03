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
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.app.i18n.Strings
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
        val strings = LocalStrings.current
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
                        ManifestUiState.Failed(strings.t("downloads.error.no_source"))
                    } catch (t: Throwable) {
                        ManifestUiState.Failed(t.message ?: t::class.simpleName ?: strings.t("downloads.error.unknown"))
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
                launchStatus = strings.t("instances.launch.no_queue")
                return
            }
            val manifest = (state as? ManifestUiState.Loaded)?.manifest
            val entry = manifest?.find(instance.versionId) ?: VersionManifestEntry(instance.versionId, "release", "")
            queue.enqueue(entry, forceRefresh = true)
            launchStatus = strings.t("instances.launch.queued", instance.versionId)
        }

        fun launchInstance(instance: Instance) {
            val details = detailService
            val detector = javaDetector
            val process = launcher
            val context = launchContext
            val service = instanceService
            val manifest = (state as? ManifestUiState.Loaded)?.manifest
            if (details == null || detector == null || process == null || context == null || service == null) {
                launchStatus = strings.t("instances.launch.no_launcher")
                return
            }
            if (!hasFiles(instance)) {
                launchStatus = strings.t("instances.launch.missing_files", instance.versionId)
                return
            }
            val provider = authProviders.minWithOrNull(compareBy({ it.priority }, { it.id }))
            if (provider == null) {
                launchStatus = strings.t("instances.launch.no_auth")
                return
            }
            // 没有清单源时退回空 URL：详情服务会优先读本地缓存
            val entry = manifest?.find(instance.versionId) ?: VersionManifestEntry(instance.versionId, "release", "")
            launchStatus = strings.t("instances.launch.starting", instance.name)
            scope.launch {
                try {
                    val detail = details.load(entry)
                    val required = detail.javaVersion?.majorVersion ?: 0
                    val runtime = detector.detect(required)
                    if (runtime == null) {
                        missingJava = required
                        pendingJavaInstance = instance
                        launchStatus = strings.t("instances.launch.missing_java", required, instance.name)
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
                    launchStatus = strings.t("instances.launch.started", instance.name, session.pid)
                } catch (t: Throwable) {
                    launchStatus = strings.t("instances.launch.failed", t.message ?: t::class.simpleName)
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
            launchStatus = strings.t("instances.launch.exited", exitedCode)
            runningSession = null
            runningInstanceId = null
        }

        fun downloadJava(required: Int) {
            val installer = javaInstaller
            val context = launchContext
            val sources = javaSources
            if (installer == null || context == null || sources == null) {
                javaMessage = strings.t("instances.java.no_source")
                return
            }
            javaBusy = true
            javaMessage = strings.t("instances.java.parsing", required)
            scope.launch {
                try {
                    // 镜像站常缺某个平台/版本；安装期失败（例如错平台的包）也会自动换下一个来源
                    val installed =
                        sources.installFirst(required, context.os, context.arch, installer) { bytes ->
                            javaMessage = strings.t("instances.java.downloaded", bytes / 1024 / 1024)
                        }
                            ?: error(
                                if (sources.hasProvider()) {
                                    strings.t("instances.java.unsupported", context.os, context.arch)
                                } else {
                                    strings.t("instances.java.no_source")
                                },
                            )
                    val (provider, home) = installed
                    javaMessage = strings.t("instances.java.installed", provider.displayName, required)
                    missingJava = null
                    javaBusy = false
                    pendingJavaInstance?.let { launchInstance(it) }
                    pendingJavaInstance = null
                } catch (t: Throwable) {
                    javaBusy = false
                    javaMessage = strings.t("instances.java.failed", t.message ?: t::class.simpleName)
                }
            }
        }

        PageScaffold(
            title = strings.t("page.instances.title"),
            subtitle = strings.t("instances.subtitle"),
            actions = {
                if (hasSource) {
                    OutlinedButton(
                        onClick = { refresh(force = true) },
                        enabled = state != ManifestUiState.Loading,
                    ) {
                        Text(if (state == ManifestUiState.Loading) strings.t("downloads.loading") else strings.t("downloads.refresh"))
                    }
                }
                if (instanceService != null) {
                    Button(onClick = { openDownloads() }) { Text(strings.t("home.install_new")) }
                }
            },
        ) {
            val account = authProviders.minWithOrNull(compareBy({ it.priority }, { it.id }))
            val missing = instances.filter { !hasFiles(it) }
            Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.m)) {
                Text(
                    text = if (account == null) {
                        strings.t("instances.account.none")
                    } else {
                        strings.t("instances.account", account.displayName, launchContext?.playerName ?: "Player")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (account == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!hasSource) {
                    NoSourceGuide(folder = pluginFolder)
                    Text(
                        text = strings.t("instances.no_source_hint"),
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
                            text = strings.t("instances.missing_files", missing.size),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { openDownloads(missing.first().versionId) }) { Text(strings.t("instances.action.go_downloads")) }
                    }
                }

                if (instances.isEmpty()) {
                    EmptyState(
                        icon = UiIcon.GAME,
                        text = strings.t("instances.empty.title"),
                        hint = if (hasSource) {
                            strings.t("instances.empty.hint_install")
                        } else {
                            strings.t("instances.empty.hint_no_source")
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
                        title = strings.t("instances.log.title", logOwnerName),
                        lines = logLines,
                        running = runningSession != null,
                        exitCode = exitedCode,
                        onClear = { logLines = emptyList() },
                        onCopy = {
                            clipboard.setText(AnnotatedString(logLines.joinToString("\n")))
                            launchStatus = strings.t("instances.log.copied")
                        },
                        onExport = {
                            launchStatus =
                                exportLog(logLines.joinToString("\n"), strings).fold(
                                    onSuccess = { strings.t("instances.log.exported", it) },
                                    onFailure = { strings.t("instances.log.export_failed", it.message ?: it::class.simpleName) },
                                )
                        },
                        onStop = {
                            runningSession?.stop()
                            launchStatus = strings.t("instances.log.stop_requested")
                        },
                        onForceStop = {
                            runningSession?.forceStop()
                            launchStatus = strings.t("instances.log.force_stopped")
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
                                strings.t("instances.java.missing_hint", required)
                            } else {
                                strings.t("instances.java.missing_from", required, provider.displayName)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { downloadJava(required) }, enabled = provider != null && !javaBusy) {
                            Text(if (javaBusy) strings.t("downloads.task.downloading") else strings.t("instances.java.download", required))
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
                            Text(strings.t("instances.manifest.loading"), style = MaterialTheme.typography.labelSmall)
                        }
                    is ManifestUiState.Failed ->
                        Text(
                            text = strings.t("instances.manifest.failed", current.message),
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
                            strings.t("instances.params.saved", target.name)
                        } else {
                            strings.t("instances.params.save_failed")
                        }
                },
            )
        }

        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(strings.t("instances.delete.title")) },
                text = { Text(strings.t("instances.delete.body", target.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            instanceService?.delete(target.id)
                            refreshInstances()
                            pendingDelete = null
                            launchStatus = strings.t("instances.delete.done", target.name)
                        },
                    ) { Text(strings.t("instances.action.delete")) }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(strings.t("instances.action.cancel")) } },
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
    val strings = LocalStrings.current
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
                text = strings.t("instances.card.version", instance.versionId, instance.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (missingFiles) {
                Text(
                    text = strings.t("instances.card.missing"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Button(onClick = onLaunch, enabled = !running) {
            Text(if (running) strings.t("instances.action.running") else strings.t("instances.action.launch"))
        }
        onRepair?.let { TextButton(onClick = it, enabled = !running) { Text(strings.t("instances.action.repair")) } }
        onEditParams?.let { TextButton(onClick = it, enabled = !running) { Text(strings.t("instances.action.params")) } }
        TextButton(onClick = onDelete, enabled = !running) { Text(strings.t("instances.action.delete")) }
    }
}

/** 实例页的固定页面 id（首页等其它页面跳转用）。 */
const val INSTANCES_PAGE_ID: String = "dev.mmcl.page.instances"

/** 用系统"另存为"对话框导出日志；取消或不选路径时返回失败原因。 */
private fun exportLog(text: String, strings: Strings): Result<Path> =
    runCatching {
        val dialog = FileDialog(null as Frame?, strings.t("instances.log.dialog_title"), FileDialog.SAVE)
        dialog.file = "latest.log"
        dialog.isVisible = true
        val directory = dialog.directory ?: error(strings.t("instances.log.no_directory"))
        val name = dialog.file ?: error(strings.t("instances.log.no_name"))
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
    val strings = LocalStrings.current
    var memoryText by remember(instance) { mutableStateOf(instance.memoryMb?.toString() ?: "") }
    var jvmArgs by remember(instance) { mutableStateOf(instance.extraJvmArgs) }
    var envVars by remember(instance) { mutableStateOf(instance.envVars) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.t("instances.params.title", instance.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.s)) {
                OutlinedTextField(
                    value = memoryText,
                    onValueChange = { memoryText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(strings.t("instances.params.memory", DEFAULT_MEMORY_MB)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = jvmArgs,
                    onValueChange = { jvmArgs = it },
                    label = { Text(strings.t("instances.params.jvm")) },
                    placeholder = { Text("-XX:+UseG1GC") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = envVars,
                    onValueChange = { envVars = it },
                    label = { Text(strings.t("instances.params.env")) },
                    placeholder = { Text("JAVA_TOOL_OPTIONS=-Dfoo=bar") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(memoryText.toIntOrNull(), jvmArgs, envVars) }) { Text(strings.t("instances.action.save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.t("instances.action.cancel")) } },
    )
}
