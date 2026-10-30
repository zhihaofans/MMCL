package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.core.download.DownloadProgress
import dev.mmcl.core.download.DownloadReport
import dev.mmcl.core.download.InstallPlan
import dev.mmcl.ui.kit.MmclSpacing
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 版本准备卡片：检查文件 / 下载游戏文件 / 进度 / 结果。
 *
 * 纯展示 + 回调，状态由 [InstancesPage] 持有；渲染测试也直接用它出图。
 */
@Composable
internal fun GamePrepCard(
    versionId: String,
    plan: InstallPlan?,
    progress: DownloadProgress?,
    report: DownloadReport?,
    failure: String?,
    javaStatus: String? = null,
    onLaunch: (() -> Unit)? = null,
    launchStatus: String? = null,
    running: Boolean = false,
    onStop: () -> Unit = {},
    busy: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(MmclSpacing.l),
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        Text(text = "准备版本 $versionId", style = MaterialTheme.typography.titleSmall)

        val status =
            when {
                failure != null -> "失败：$failure"
                report != null ->
                    if (report.isSuccess) {
                        "完成：下载 ${report.downloaded} · 跳过 ${report.skipped}"
                    } else {
                        "完成但有问题：下载 ${report.downloaded} · 跳过 ${report.skipped} · 失败 ${report.failures.size}"
                    }

                progress != null -> "下载中 ${progress.completed}/${progress.total}：${progress.current}"
                plan != null -> "需要 ${plan.fileCount} 个文件，约 ${formatBytes(plan.totalBytes)}" +
                    if (plan.warnings.isEmpty()) "" else "（${plan.warnings.size} 项暂不支持）"

                else -> "先「检查文件」看看要下载多少内容，或直接开始下载"
            }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (javaStatus != null) {
            Text(
                text = javaStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (busy) {
            val fraction =
                progress?.let { if (it.total == 0) 0f else it.completed.toFloat() / it.total } ?: 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = MmclSpacing.xs),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = MmclSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
        ) {
            OutlinedButton(onClick = onCheck, enabled = !busy) { Text("检查文件") }
            Button(onClick = onDownload, enabled = !busy) { Text("下载游戏文件") }
        }

        if (onLaunch != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MmclSpacing.s),
                horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
            ) {
                Button(onClick = onLaunch, enabled = !running) { Text(if (running) "运行中" else "启动游戏") }
                if (running) {
                    OutlinedButton(onClick = onStop) { Text("停止游戏") }
                }
            }
            if (launchStatus != null) {
                Text(
                    text = launchStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (report != null && report.failures.isNotEmpty()) {
            Text(
                text = "首个失败：${report.failures.first().file.relativePath}（" +
                    "${report.failures.first().error.message ?: "未知错误"}）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 人类可读的字节数（按 1000 进制，和下载器显示的 MB 一致）。 */
internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0)
        else -> "${(bytes.toDouble()).roundToInt()} B"
    }
