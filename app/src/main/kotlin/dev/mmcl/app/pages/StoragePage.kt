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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.core.instance.InstanceService
import dev.mmcl.core.storage.DeleteBlock
import dev.mmcl.core.storage.DeleteOutcome
import dev.mmcl.core.storage.StorageCategoryId
import dev.mmcl.core.storage.StorageInstance
import dev.mmcl.core.storage.StorageRuntime
import dev.mmcl.core.storage.StorageService
import dev.mmcl.core.storage.StorageSnapshot
import dev.mmcl.core.storage.StorageVersion
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import java.nio.file.Path
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 存储页：统计实例、版本、运行时与共享资源的占用，并安全删除未使用的内容。
 *
 * 只显示占用、不清理孤立 `libraries` / `assets`（需要全量引用扫描，后续版本再做）。
 */
class StoragePage(
    private val storage: StorageService? = null,
    private val instanceService: InstanceService? = null,
    private val runningInstanceProvider: () -> String? = { null },
    private val protectedRuntimes: () -> Set<Path> = { emptySet() },
) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "存储"
    override val titleKey: String = "page.storage.title"
    override val icon: UiIcon = UiIcon.WRENCH
    override val group: UiGroup = UiGroup.SYSTEM
    override val order: Int = 20

    @Composable
    override fun Content(navigator: AppNavigator) {
        val strings = LocalStrings.current
        val scope = rememberCoroutineScope()
        var snapshot by remember { mutableStateOf<StorageSnapshot?>(null) }
        var scanning by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var pending by remember { mutableStateOf<PendingDelete?>(null) }

        fun runningVersionIds(): Set<String> {
            val runningId = runningInstanceProvider() ?: return emptySet()
            val versionId = instanceService?.list()?.firstOrNull { it.id == runningId }?.versionId ?: return emptySet()
            return setOf(versionId)
        }

        fun refresh() {
            val service = storage ?: return
            scanning = true
            scope.launch {
                snapshot =
                    withContext(Dispatchers.IO) {
                        service.scan(
                            protectedRuntimes = protectedRuntimes(),
                            runningVersionIds = runningVersionIds(),
                        )
                    }
                scanning = false
            }
        }

        fun blockText(reason: DeleteBlock): String =
            when (reason) {
                DeleteBlock.USED_BY_INSTANCE -> strings.t("storage.block.used_by_instance")
                DeleteBlock.INHERITED_BY -> strings.t("storage.block.inherited_by")
                DeleteBlock.RUNNING -> strings.t("storage.block.running")
                DeleteBlock.PROTECTED -> strings.t("storage.block.protected")
                DeleteBlock.INVALID_TARGET -> strings.t("storage.block.invalid")
                DeleteBlock.NOT_FOUND -> strings.t("storage.block.not_found")
            }

        fun apply(outcome: DeleteOutcome) {
            message =
                when (outcome) {
                    is DeleteOutcome.Deleted -> strings.t("storage.delete.done", formatBytes(outcome.bytes))
                    is DeleteOutcome.Blocked ->
                        strings.t("storage.delete.blocked", blockText(outcome.reason)) +
                            (outcome.detail?.let { "（$it）" } ?: "")
                    is DeleteOutcome.Failed -> strings.t("storage.delete.failed", outcome.message)
                }
            refresh()
        }

        fun performDelete(target: PendingDelete) {
            val service = storage ?: return
            pending = null
            scope.launch {
                val outcome =
                    withContext(Dispatchers.IO) {
                        when (target) {
                            is PendingDelete.InstanceTarget -> {
                                val deleted = instanceService?.delete(target.item.id) == true
                                if (deleted) DeleteOutcome.Deleted(target.item.bytes) else DeleteOutcome.Failed(strings.t("storage.delete.instance_failed"))
                            }
                            is PendingDelete.VersionTarget -> service.deleteVersion(target.item.id, runningVersionIds())
                            is PendingDelete.RuntimeTarget -> service.deleteRuntime(target.item.relativePath, protectedRuntimes())
                            PendingDelete.CacheTarget -> service.clearCache()
                        }
                    }
                apply(outcome)
            }
        }

        LaunchedEffect(Unit) { refresh() }

        PageScaffold(
            title = strings.t("page.storage.title"),
            subtitle = strings.t("storage.subtitle"),
            actions = {
                OutlinedButton(onClick = { refresh() }, enabled = !scanning) {
                    Text(if (scanning) strings.t("storage.scanning") else strings.t("storage.refresh"))
                }
            },
        ) {
            val current = snapshot
            if (current == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(MmclSpacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(MmclSpacing.xxl))
                }
                return@PageScaffold
            }

            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MmclSpacing.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.t("storage.total"), style = MaterialTheme.typography.labelMedium)
                        Text(formatBytes(current.totalBytes), style = MaterialTheme.typography.headlineSmall)
                    }
                    message?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (current.warnings.isNotEmpty()) {
                    current.warnings.take(3).forEach { warning ->
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                StorageSection(title = strings.t("storage.section.instances")) {
                    if (current.instances.isEmpty()) {
                        EmptyLine(strings.t("storage.empty.instances"))
                    }
                    current.instances.forEach { item ->
                        val running = runningInstanceProvider() == item.id
                        StorageRow(
                            title = item.name,
                            detail =
                                if (running) {
                                    strings.t("storage.instance.running", item.versionId)
                                } else {
                                    strings.t("storage.instance.detail", item.versionId)
                                },
                            bytes = item.bytes,
                            action = {
                                TextButton(
                                    onClick = { pending = PendingDelete.InstanceTarget(item) },
                                    enabled = !running,
                                ) { Text(strings.t("storage.delete")) }
                            },
                        )
                    }
                }

                StorageSection(title = strings.t("storage.section.game_versions")) {
                    if (current.gameVersions.isEmpty()) {
                        EmptyLine(strings.t("storage.empty.versions"))
                    }
                    current.gameVersions.forEach { item ->
                        VersionRow(item, strings.t("storage.section.game_versions")) { pending = PendingDelete.VersionTarget(item) }
                    }
                }

                StorageSection(title = strings.t("storage.section.loader_versions")) {
                    if (current.loaderVersions.isEmpty()) {
                        EmptyLine(strings.t("storage.empty.loader_versions"))
                    }
                    current.loaderVersions.forEach { item ->
                        VersionRow(item, strings.t("storage.section.loader_versions")) { pending = PendingDelete.VersionTarget(item) }
                    }
                }

                StorageSection(title = strings.t("storage.section.runtimes")) {
                    if (current.runtimes.isEmpty()) {
                        EmptyLine(strings.t("storage.empty.runtimes"))
                    }
                    current.runtimes.forEach { item ->
                        StorageRow(
                            title = item.relativePath,
                            detail =
                                if (item.protected) {
                                    strings.t("storage.runtime.protected", item.major?.toString() ?: "?")
                                } else {
                                    strings.t("storage.runtime.detail", item.major?.toString() ?: "?")
                                },
                            bytes = item.bytes,
                            action = {
                                TextButton(
                                    onClick = { pending = PendingDelete.RuntimeTarget(item) },
                                    enabled = !item.protected,
                                ) { Text(strings.t("storage.delete")) }
                            },
                        )
                    }
                }

                StorageSection(title = strings.t("storage.section.shared")) {
                    HorizontalDivider()
                    val cacheBytes = current.categories.firstOrNull { it.id == StorageCategoryId.CACHE }?.bytes ?: 0
                    current.categories
                        .filter { it.id in SHARED_CATEGORIES }
                        .forEach { category ->
                            StorageRow(
                                title = strings.t(categoryKey(category.id)),
                                detail = strings.t("storage.shared.read_only"),
                                bytes = category.bytes,
                                action =
                                    if (category.id == StorageCategoryId.CACHE && cacheBytes > 0) {
                                        {
                                            TextButton(onClick = { pending = PendingDelete.CacheTarget }) {
                                                Text(strings.t("storage.cache.clear"))
                                            }
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                }
            }
        }

        pending?.let { target ->
            val body =
                when (target) {
                    is PendingDelete.InstanceTarget ->
                        strings.t("storage.delete.instance", target.item.name, formatBytes(target.item.bytes))
                    is PendingDelete.VersionTarget ->
                        strings.t("storage.delete.version", target.item.id, formatBytes(target.item.bytes))
                    is PendingDelete.RuntimeTarget ->
                        strings.t("storage.delete.runtime", target.item.relativePath, formatBytes(target.item.bytes))
                    PendingDelete.CacheTarget ->
                        strings.t(
                            "storage.delete.cache",
                            formatBytes(snapshot?.categories?.firstOrNull { it.id == StorageCategoryId.CACHE }?.bytes ?: 0),
                        )
                }
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text(strings.t("storage.delete.title")) },
                text = { Text(body) },
                confirmButton = {
                    TextButton(onClick = { performDelete(target) }) { Text(strings.t("storage.delete.confirm")) }
                },
                dismissButton = {
                    TextButton(onClick = { pending = null }) { Text(strings.t("storage.delete.cancel")) }
                },
            )
        }
    }

    @Composable
    private fun VersionRow(item: StorageVersion, section: String, onDelete: () -> Unit) {
        val strings = LocalStrings.current
        val detail =
            buildString {
                if (item.baseVersionId != null) {
                    append(strings.t("storage.loader.base", item.baseVersionId))
                    append(" · ")
                }
                when (item.blocked) {
                    dev.mmcl.core.storage.VersionBlock.USED_BY_INSTANCE ->
                        append(strings.t("storage.version.used_by", item.usedByInstanceIds.size))
                    dev.mmcl.core.storage.VersionBlock.INHERITED_BY ->
                        append(strings.t("storage.version.inherited_by", item.inheritedBy.size))
                    dev.mmcl.core.storage.VersionBlock.RUNNING ->
                        append(strings.t("storage.version.running"))
                    null ->
                        if (item.usedByInstanceIds.isEmpty() && item.inheritedBy.isEmpty()) {
                            append(strings.t("storage.version.unused"))
                        }
                }
            }
        StorageRow(
            title = item.id,
            detail = "$section · $detail",
            bytes = item.bytes,
            action = {
                TextButton(onClick = onDelete, enabled = item.deletable) { Text(strings.t("storage.delete")) }
            },
        )
    }

    private sealed interface PendingDelete {
        data object CacheTarget : PendingDelete

        data class InstanceTarget(val item: StorageInstance) : PendingDelete

        data class VersionTarget(val item: StorageVersion) : PendingDelete

        data class RuntimeTarget(val item: StorageRuntime) : PendingDelete
    }

    companion object {
        /** 页面 id，供其他页面跳转。 */
        const val PAGE_ID: String = "dev.mmcl.page.storage"

        private val SHARED_CATEGORIES =
            setOf(
                StorageCategoryId.LIBRARIES,
                StorageCategoryId.ASSETS,
                StorageCategoryId.CACHE,
                StorageCategoryId.LOGS,
                StorageCategoryId.PLUGINS,
                StorageCategoryId.OTHER,
            )

        private fun categoryKey(id: StorageCategoryId): String = "storage.category." + id.name.lowercase(Locale.ROOT)
    }
}

@Composable
private fun StorageSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .padding(MmclSpacing.l),
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StorageRow(
    title: String,
    detail: String,
    bytes: Long,
    action: (@Composable () -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatBytes(bytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(MmclSpacing.xs))
        action?.invoke()
    }
}

/** 把字节数格式化为 B / KB / MB / GB。 */
internal fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.ROOT, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.ROOT, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.ROOT, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
