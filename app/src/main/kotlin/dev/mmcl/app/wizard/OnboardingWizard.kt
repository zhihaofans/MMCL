package dev.mmcl.app.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.app.pages.NoSourceGuide
import dev.mmcl.app.pages.PluginFolder
import dev.mmcl.core.download.DownloadTaskState
import dev.mmcl.core.download.DownloadsService
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.core.java.JavaRuntimeSources
import dev.mmcl.core.version.VersionManifest
import dev.mmcl.core.version.VersionManifestEntry
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.ui.kit.MmclSpacing

/** 设置项：向导是否已经完成（`true` 后不再自动弹出，设置页仍可重新打开）。 */
const val WIZARD_DONE_KEY: String = "ui.onboarded"

/** 版本清单在向导里的加载状态。 */
private sealed interface WizardManifestState {
    data object Idle : WizardManifestState

    data object Loading : WizardManifestState

    data class Loaded(val manifest: VersionManifest) : WizardManifestState

    data class Failed(val message: String) : WizardManifestState
}

/**
 * 首次启动向导：下载源 →（可选）Java 运行时来源 → 选版本 → 安装并建实例。
 *
 * 只编排现有服务，不新增下载/安装逻辑；「跳过」会写入 [WIZARD_DONE_KEY]。
 */
@Composable
fun OnboardingWizard(
    manifests: VersionManifestService?,
    downloads: DownloadsService?,
    instanceService: InstanceService?,
    javaSources: JavaRuntimeSources?,
    pluginFolder: PluginFolder?,
    onFinished: () -> Unit,
) {
    val strings = LocalStrings.current
    val hasSource = manifests?.hasSource() == true
    var step by remember { mutableStateOf(WizardStep.SOURCE) }
    var selectedSourceId by remember { mutableStateOf(manifests?.currentSource()?.id) }
    var selectedJavaId by remember { mutableStateOf(javaSources?.selectedId()) }
    var selectedVersion by remember { mutableStateOf<VersionManifestEntry?>(null) }
    var manifestState by remember { mutableStateOf<WizardManifestState>(WizardManifestState.Idle) }
    var instanceName by remember { mutableStateOf<String?>(null) }

    val taskFlow = downloads?.tasks
    val tasks = if (taskFlow != null) taskFlow.collectAsState().value else emptyList()
    val task = selectedVersion?.let { version -> tasks.lastOrNull { it.versionId == version.id } }

    // 进入「选版本」时才加载清单，避免第一步就发网络请求
    LaunchedEffect(step) {
        if (step == WizardStep.VERSION && manifestState is WizardManifestState.Idle) {
            val service = manifests
            if (service == null) {
                manifestState = WizardManifestState.Failed(strings.t("wizard.source.none"))
                return@LaunchedEffect
            }
            manifestState = WizardManifestState.Loading
            manifestState =
                try {
                    val manifest = service.load()
                    selectedVersion = selectedVersion ?: manifest.latestRelease()
                    WizardManifestState.Loaded(manifest)
                } catch (t: Throwable) {
                    WizardManifestState.Failed(t.message.orEmpty())
                }
        }
    }

    // 安装完成后自动建实例，只建一次
    LaunchedEffect(task?.state, instanceName, selectedVersion) {
        val version = selectedVersion
        if (task?.state == DownloadTaskState.COMPLETED && instanceName == null && version != null) {
            instanceName = runCatching { instanceService?.create(version.id)?.name }.getOrNull()
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(MmclSpacing.xl)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.t("wizard.title"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    strings.t("wizard.step", WizardFlow.position(step), WizardFlow.total()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onFinished) { Text(strings.t("wizard.skip")) }
        }
        HorizontalDivider()

        when (step) {
            WizardStep.SOURCE ->
                SourceStep(
                    hasSource = hasSource,
                    manifests = manifests,
                    pluginFolder = pluginFolder,
                    selectedSourceId = selectedSourceId,
                    onSelect = { id ->
                        selectedSourceId = id
                        manifests?.selectSource(id)
                    },
                )

            WizardStep.JAVA ->
                JavaStep(
                    javaSources = javaSources,
                    selectedJavaId = selectedJavaId,
                    onSelect = { id ->
                        selectedJavaId = id
                        javaSources?.select(id)
                    },
                )

            WizardStep.VERSION ->
                VersionStep(
                    state = manifestState,
                    selectedVersion = selectedVersion,
                    onSelect = { selectedVersion = it },
                    onRetry = { manifestState = WizardManifestState.Idle },
                )

            WizardStep.INSTALL ->
                InstallStep(
                    version = selectedVersion,
                    task = task,
                    instanceName = instanceName,
                    onStart = { selectedVersion?.let { downloads?.enqueue(it) } },
                    onFinish = onFinished,
                )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s)) {
            TextButton(
                onClick = { step = WizardFlow.previous(step) },
                enabled = step != WizardStep.SOURCE,
            ) { Text(strings.t("wizard.back")) }

            if (step != WizardStep.INSTALL) {
                val canNext =
                    WizardFlow.next(step, hasSource) != null &&
                        (step != WizardStep.VERSION || selectedVersion != null)
                Button(
                    onClick = { WizardFlow.next(step, hasSource)?.let { step = it } },
                    enabled = canNext,
                ) { Text(strings.t("wizard.next")) }
            }
        }
    }
}

@Composable
private fun SourceStep(
    hasSource: Boolean,
    manifests: VersionManifestService?,
    pluginFolder: PluginFolder?,
    selectedSourceId: String?,
    onSelect: (String) -> Unit,
) {
    val strings = LocalStrings.current
    Text(strings.t("wizard.source.title"), style = MaterialTheme.typography.titleMedium)
    if (!hasSource) {
        Text(
            strings.t("wizard.source.hint"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NoSourceGuide(folder = pluginFolder)
        Text(
            strings.t("wizard.source.restart_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    Text(
        strings.t("wizard.source.hint"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    manifests?.orderedSources().orEmpty().forEach { source ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = source.id == selectedSourceId,
                onClick = { onSelect(source.id) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(source.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    source.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun JavaStep(
    javaSources: JavaRuntimeSources?,
    selectedJavaId: String?,
    onSelect: (String) -> Unit,
) {
    val strings = LocalStrings.current
    Text(strings.t("wizard.java.title"), style = MaterialTheme.typography.titleMedium)
    val providers = javaSources?.all().orEmpty()
    if (providers.isEmpty()) {
        Text(
            strings.t("wizard.java.none"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        strings.t("wizard.java.hint"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    providers.forEach { provider ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = provider.id == selectedJavaId,
                onClick = { onSelect(provider.id) },
            )
            Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun VersionStep(
    state: WizardManifestState,
    selectedVersion: VersionManifestEntry?,
    onSelect: (VersionManifestEntry) -> Unit,
    onRetry: () -> Unit,
) {
    val strings = LocalStrings.current
    Text(strings.t("wizard.version.title"), style = MaterialTheme.typography.titleMedium)
    when (state) {
        WizardManifestState.Idle, WizardManifestState.Loading ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(strings.t("wizard.version.loading"))
            }

        is WizardManifestState.Failed -> {
            Text(
                strings.t("wizard.version.failed"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRetry) { Text(strings.t("wizard.version.retry")) }
        }

        is WizardManifestState.Loaded -> {
            state.manifest.versions.filter { it.isRelease }.take(6).forEach { version ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = version.id == selectedVersion?.id,
                        onClick = { onSelect(version) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(version.id, style = MaterialTheme.typography.titleSmall)
                        Text(
                            version.type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallStep(
    version: VersionManifestEntry?,
    task: dev.mmcl.core.download.DownloadTask?,
    instanceName: String?,
    onStart: () -> Unit,
    onFinish: () -> Unit,
) {
    val strings = LocalStrings.current
    Text(strings.t("wizard.install.title"), style = MaterialTheme.typography.titleMedium)
    if (version == null) {
        Text(strings.t("wizard.version.title"), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text(
        strings.t("wizard.install.hint", version.id),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val active = task?.state?.isActive == true
    val completed = task?.state == DownloadTaskState.COMPLETED
    Button(onClick = onStart, enabled = !active && !completed) {
        Text(strings.t("wizard.install.start"))
    }
    when (task?.state) {
        DownloadTaskState.QUEUED, DownloadTaskState.PLANNING, DownloadTaskState.DOWNLOADING -> {
            val progress = task.progress
            Text(
                buildString {
                    append(strings.t("wizard.install.running"))
                    if (progress != null && progress.total > 0) {
                        append(" ")
                        append(progress.completed)
                        append("/")
                        append(progress.total)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        DownloadTaskState.COMPLETED -> {
            Text(
                strings.t("wizard.install.done", version.id),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            instanceName?.let {
                Text(
                    strings.t("wizard.instance.created", it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onFinish) { Text(strings.t("wizard.finish")) }
        }

        DownloadTaskState.FAILED ->
            Text(
                strings.t("wizard.install.failed", task.error.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

        DownloadTaskState.CANCELLED ->
            Text(
                strings.t("wizard.install.failed", ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

        null -> Unit
    }
}
