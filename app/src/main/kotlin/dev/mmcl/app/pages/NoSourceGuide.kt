package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.app.platform.LinkOpener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.mmcl.ui.kit.MmclSpacing

/** 应用 Release 页面：源插件 JAR 随 Release 分发，用户不需要自己编译。 */
private const val RELEASE_URL = "https://github.com/zhihaofans/MMCL/releases/latest"

/**
 * 没有 `VersionManifestSource` 插件时的引导卡片。
 *
 * 应用**不内置任何下载地址**，所以这里不能"帮你装"，只能把「从 Release 下载插件 →
 * 放进插件目录 → 重启」这条路径写清楚，并提供打开 Release / 打开目录 / 复制地址。
 * Release 页面是启动器自己的分发页，不是游戏下载源。
 *
 * @param folder 插件目录与打开方式；为 `null` 时只显示说明（例如测试环境）
 */
@Composable
fun NoSourceGuide(
    folder: PluginFolder?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var actionMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        Text(
            text = strings.t("no_source.title"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = strings.t("no_source.body"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(strings.t("no_source.steps"), style = MaterialTheme.typography.titleSmall)
        StepRow("1", strings.t("no_source.step1"), RELEASE_URL, mono = true)
        StepRow("2", strings.t("no_source.step2"), folder?.path?.toString() ?: "<data>/plugins/")
        StepRow("3", strings.t("no_source.step3"), strings.t("no_source.step3_hint"))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
        ) {
            Button(
                onClick = {
                    val result = folder?.open()
                    actionMessage =
                        when {
                            result == null -> strings.t("no_source.no_folder")
                            result.isSuccess -> strings.t("no_source.opened")
                            else ->
                                strings.t(
                                    "no_source.open_failed",
                                    result.exceptionOrNull()?.message ?: strings.t("no_source.unknown_reason"),
                                )
                        }
                },
                enabled = folder != null,
            ) { Text(strings.t("no_source.open")) }
            OutlinedButton(
                onClick = {
                    LinkOpener.open(RELEASE_URL)
                        .onSuccess { actionMessage = null }
                        .onFailure {
                            actionMessage = strings.t("no_source.release_failed", it.message ?: strings.t("no_source.unknown_reason"))
                        }
                },
            ) { Text(strings.t("no_source.release")) }
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(RELEASE_URL))
                    actionMessage = strings.t("no_source.copied")
                },
            ) { Text(strings.t("no_source.copy")) }
        }

        actionMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepRow(index: String, title: String, detail: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = MmclSpacing.s, vertical = MmclSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        Text(
            text = index,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
