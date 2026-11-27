package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.api.capability.ContentProject
import dev.mmcl.api.capability.ContentSearchQuery
import dev.mmcl.api.capability.ContentSource
import dev.mmcl.api.capability.ContentVersion
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.core.content.ContentInstallRequest
import dev.mmcl.core.content.ContentInstaller
import dev.mmcl.core.content.InstalledMod
import dev.mmcl.core.content.ModManager
import dev.mmcl.core.instance.Instance
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内容页：选实例 → 搜索 Modrinth → 选版本 → 安装到实例 `mods/`。
 *
 * 只做 Fabric + mod；资源包/光影包与整合包留到后续。内容来源由插件提供，
 * 应用本身不含任何内容地址。
 */
class ContentPage(
    private val instances: InstanceService? = null,
    private val sources: () -> List<ContentSource> = { emptyList() },
    private val installer: ContentInstaller? = null,
    private val modManager: ModManager? = null,
    private val dataDir: Path? = null,
) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "内容"
    override val titleKey: String = "page.content.title"
    override val icon: UiIcon = UiIcon.EXTENSION
    override val group: UiGroup = UiGroup.MAIN
    override val order: Int = 15

    @Composable
    override fun Content(navigator: AppNavigator) {
        val strings = LocalStrings.current
        val scope = rememberCoroutineScope()
        val allInstances = remember(instances) { instances?.list().orEmpty() }
        var instanceId by remember { mutableStateOf(allInstances.firstOrNull()?.id) }
        var instanceMenuOpen by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var searching by remember { mutableStateOf(false) }
        var results by remember { mutableStateOf<List<ContentProject>>(emptyList()) }
        var project by remember { mutableStateOf<ContentProject?>(null) }
        var versions by remember { mutableStateOf<List<ContentVersion>>(emptyList()) }
        var loadingVersions by remember { mutableStateOf(false) }
        var installing by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<String?>(null) }
        var installed by remember { mutableStateOf<List<InstalledMod>>(emptyList()) }
        var pendingDelete by remember { mutableStateOf<InstalledMod?>(null) }

        val source = sources().firstOrNull()
        val instance = allInstances.firstOrNull { it.id == instanceId }
        val loader = instance?.let { loaderOf(it) }

        fun instanceDirectory(target: Instance): Path? = dataDir?.resolve("instances")?.resolve(target.id)

        fun refreshInstalled() {
            val target = instance ?: return
            val manager = modManager ?: return
            val dir = instanceDirectory(target) ?: return
            installed = manager.list(dir)
        }

        LaunchedEffect(instanceId) { refreshInstalled() }

        fun toggleMod(mod: InstalledMod) {
            val target = instance ?: return
            val manager = modManager ?: return
            val dir = instanceDirectory(target) ?: return
            manager
                .setEnabled(dir, mod.fileName, !mod.enabled)
                .onSuccess {
                    status =
                        strings.t(
                            "content.installed.toggled",
                            strings.t(if (mod.enabled) "content.installed.disable" else "content.installed.enable"),
                            mod.name,
                        )
                    refreshInstalled()
                }
                .onFailure { status = strings.t("content.installed.failed", it.message ?: it::class.simpleName.orEmpty()) }
        }

        fun deleteMod(mod: InstalledMod) {
            val target = instance ?: return
            val manager = modManager ?: return
            val dir = instanceDirectory(target) ?: return
            manager
                .delete(dir, mod.fileName)
                .onSuccess {
                    status = strings.t("content.installed.deleted", mod.name)
                    refreshInstalled()
                }
                .onFailure { status = strings.t("content.installed.failed", it.message ?: it::class.simpleName.orEmpty()) }
        }

        fun clearResults() {
            results = emptyList()
            project = null
            versions = emptyList()
            status = null
        }

        fun search() {
            val current = source ?: return
            val target = instance ?: return
            if (loader == null) {
                status = strings.t("content.unsupported")
                return
            }
            searching = true
            clearResults()
            scope.launch {
                try {
                    results =
                        withContext(Dispatchers.IO) {
                            current.search(
                                ContentSearchQuery(
                                    query = query,
                                    gameVersion = baseVersionOf(target),
                                    loader = loaderOf(target),
                                    limit = 20,
                                ),
                            )
                        }
                    if (results.isEmpty()) status = strings.t("content.results.empty")
                } catch (t: Throwable) {
                    status = strings.t("content.install.failed", t.message ?: t::class.simpleName.orEmpty())
                } finally {
                    searching = false
                }
            }
        }

        fun selectProject(selected: ContentProject) {
            val current = source ?: return
            val target = instance ?: return
            project = selected
            loadingVersions = true
            scope.launch {
                try {
                    versions =
                        withContext(Dispatchers.IO) {
                            current.versions(selected.id, baseVersionOf(target), loaderOf(target))
                        }
                    if (versions.isEmpty()) status = strings.t("content.versions.empty")
                } catch (t: Throwable) {
                    status = strings.t("content.install.failed", t.message ?: t::class.simpleName.orEmpty())
                } finally {
                    loadingVersions = false
                }
            }
        }

        fun install(version: ContentVersion) {
            val current = source ?: return
            val target = instance ?: return
            val contentInstaller = installer ?: return
            val root = dataDir ?: return
            installing = true
            status = strings.t("content.installing")
            scope.launch {
                try {
                    val result =
                        withContext(Dispatchers.IO) {
                            contentInstaller.install(
                                ContentInstallRequest(
                                    source = current,
                                    instanceDir = root.resolve("instances").resolve(target.id),
                                    gameVersion = baseVersionOf(target),
                                    loader = loaderOf(target),
                                    version = version,
                                ),
                            )
                        }
                    status = strings.t("content.install.done", result.report.downloaded, result.report.skipped, result.report.failures.size)
                    if (result.skippedExisting.isNotEmpty()) {
                        status = status + " " + strings.t("content.install.skipped", result.skippedExisting.joinToString(", "))
                    }
                    if (result.unresolvedDependencies.isNotEmpty()) {
                        status = status + " " + strings.t("content.install.unresolved", result.unresolvedDependencies.joinToString(", "))
                    }
                    refreshInstalled()
                } catch (t: Throwable) {
                    status = strings.t("content.install.failed", t.message ?: t::class.simpleName.orEmpty())
                } finally {
                    installing = false
                }
            }
        }

        PageScaffold(
            title = strings.t("page.content.title"),
            subtitle = strings.t("content.subtitle"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MmclSpacing.m),
            ) {
                if (source == null) {
                    Text(
                        text = strings.t("content.no_source"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    return@PageScaffold
                }
                Text(
                    text = strings.t("content.source", source.displayName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box {
                    OutlinedButton(onClick = { instanceMenuOpen = true }, enabled = allInstances.isNotEmpty()) {
                        Text(
                            if (instance != null) {
                                strings.t("content.instance.value", instance.name, instance.versionId)
                            } else {
                                strings.t("content.no_instances")
                            },
                        )
                    }
                    DropdownMenu(expanded = instanceMenuOpen, onDismissRequest = { instanceMenuOpen = false }) {
                        allInstances.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} · ${item.versionId}") },
                                onClick = {
                                    instanceId = item.id
                                    instanceMenuOpen = false
                                    clearResults()
                                },
                            )
                        }
                    }
                }

                if (instance != null && loader == null) {
                    Text(
                        text = strings.t("content.unsupported"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (instance != null) {
                    Text(
                        text = strings.t("content.installed.title", installed.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (installed.isEmpty()) {
                        Text(
                            text = strings.t("content.installed.empty"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    installed.forEach { mod ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .padding(MmclSpacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mod.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text =
                                        buildString {
                                            append(mod.fileName)
                                            mod.version?.let {
                                                append(" · ")
                                                append(it)
                                            }
                                            if (mod.conflict) {
                                                append(" · ")
                                                append(strings.t("content.installed.conflict"))
                                            }
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (mod.conflict) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                            TextButton(onClick = { toggleMod(mod) }) {
                                Text(
                                    strings.t(
                                        if (mod.enabled) {
                                            "content.installed.disable"
                                        } else {
                                            "content.installed.enable"
                                        },
                                    ),
                                )
                            }
                            TextButton(onClick = { pendingDelete = mod }) {
                                Text(strings.t("content.installed.delete"))
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(strings.t("content.search.hint")) },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.size(MmclSpacing.s))
                    Button(onClick = { search() }, enabled = !searching && instance != null && loader != null) {
                        Text(if (searching) strings.t("content.searching") else strings.t("content.search"))
                    }
                }

                status?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val selectedProject = project
                if (selectedProject == null) {
                    results.forEach { hit ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .clickable { selectProject(hit) }
                                    .padding(MmclSpacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hit.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    strings.t("content.downloads", hit.downloads) + " · " + hit.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedProject.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { clearResults() }) { Text(strings.t("content.back")) }
                    }
                    if (loadingVersions) {
                        Box(modifier = Modifier.fillMaxWidth().padding(MmclSpacing.l), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(MmclSpacing.xxl))
                        }
                    }
                    versions.forEach { version ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .padding(MmclSpacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(version.versionNumber.ifBlank { version.name }, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    version.gameVersions.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { install(version) }, enabled = !installing) {
                                Text(if (installing) strings.t("content.installing") else strings.t("content.install"))
                            }
                        }
                    }
                }
            }
        }
        pendingDelete?.let { mod ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(strings.t("content.installed.delete.title")) },
                text = { Text(strings.t("content.installed.delete.body", mod.name, mod.fileName)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            deleteMod(mod)
                        },
                    ) { Text(strings.t("content.installed.delete.confirm")) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(strings.t("content.installed.delete.cancel"))
                    }
                },
            )
        }
    }

    private fun baseVersionOf(instance: Instance): String {
        val root = dataDir ?: return instance.versionId
        val file = root.resolve("versions").resolve(instance.versionId).resolve("${instance.versionId}.json")
        return runCatching {
            val text = Files.readString(file)
            INHERITS_FROM.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: instance.versionId
        }.getOrDefault(instance.versionId)
    }

    private fun loaderOf(instance: Instance): String? =
        when {
            instance.versionId.startsWith("fabric-loader-") -> "fabric"
            instance.versionId.startsWith("neoforge-") -> "neoforge"
            instance.versionId.startsWith("forge-") -> "forge"
            else -> null
        }

    companion object {
        const val PAGE_ID: String = "dev.mmcl.page.content"

        private val INHERITS_FROM = Regex(""""inheritsFrom"\s*:\s*"([^"]+)"""")
    }
}
