package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.mmcl.ui.kit.MmclSpacing

/**
 * 游戏日志面板：终端风格，显示最近若干行并自动滚到底。
 *
 * - 日志由 [dev.mmcl.core.launch.LaunchSession] 的输出流驱动，行数由调用方裁剪；
 * - 退出后保留最后一段日志与退出码，方便直接看到失败原因。
 */
@Composable
internal fun GameLogPanel(
    title: String,
    lines: List<String>,
    running: Boolean,
    exitCode: Int?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(MmclSpacing.m),
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = when {
                    running -> "运行中"
                    exitCode != null -> "已退出 · code $exitCode"
                    else -> "未启动"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClear, enabled = lines.isNotEmpty()) { Text("清空") }
        }

        val scroll = rememberScrollState()
        LaunchedEffect(lines.size) {
            scroll.scrollTo(scroll.maxValue)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0B0D10))
                .padding(MmclSpacing.s),
        ) {
            if (lines.isEmpty()) {
                Text(
                    text = "（暂无输出）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                SelectionContainer {
                    Text(
                        text = lines.joinToString("\n"),
                        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD8E0EA),
                    )
                }
            }
        }
    }
}
