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

/** 安装下载源插件要执行的命令（跨平台写法都列出来，避免用户猜）。 */
private const val BUILD_COMMAND = "gradlew :plugins:source-mojang:jar"

/**
 * 没有 `VersionManifestSource` 插件时的引导卡片。
 *
 * 应用**不内置任何下载地址**，所以这里不能"帮你装"，只能把「编译插件 → 放进插件目录 → 重启」
 * 这条路径写清楚，并提供打开目录 / 复制命令两个动作。
 *
 * @param folder 插件目录与打开方式；为 `null` 时只显示说明（例如测试环境）
 */
@Composable
fun NoSourceGuide(
    folder: PluginFolder?,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var actionMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        Text(
            text = "还没有安装下载源插件",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "本应用不内置任何下载地址：版本清单和游戏文件都来自插件。" +
                "装一个 source-mojang（或其它源插件）之后才能拉取版本。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("三步安装", style = MaterialTheme.typography.titleSmall)
        StepRow("1", "编译插件（项目根目录）", BUILD_COMMAND, mono = true)
        StepRow("2", "把生成的 JAR 复制到插件目录", folder?.path?.toString() ?: "<数据目录>/plugins/")
        StepRow("3", "重启启动器", "插件只在启动时扫描一次")

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
        ) {
            Button(
                onClick = {
                    val result = folder?.open()
                    actionMessage =
                        when {
                            result == null -> "这里没有插件目录信息"
                            result.isSuccess -> "已在文件管理器中打开"
                            else -> "打开失败：${result.exceptionOrNull()?.message ?: "未知原因"}"
                        }
                },
                enabled = folder != null,
            ) { Text("打开插件目录") }
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(BUILD_COMMAND))
                    actionMessage = "命令已复制"
                },
            ) { Text("复制命令") }
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
