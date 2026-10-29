package dev.mmcl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.mmcl.api.LauncherInfo
import dev.mmcl.core.builtin.BuiltinPluginSync
import dev.mmcl.core.platform.Platforms
import dev.mmcl.core.plugin.LoadedPlugin
import dev.mmcl.core.plugin.PluginManager
import java.nio.file.Files
import org.slf4j.LoggerFactory

/**
 * 启动入口：准备数据目录 → 同步内置插件 → 加载插件 → 打开 Compose 窗口。
 *
 * 0.0.1 阶段还没有启动游戏的执行链，窗口只负责把运行状态如实展示出来。
 */
fun main() {
    val logger = LoggerFactory.getLogger("dev.mmcl.app")
    val launcher = Platforms.launcherInfo(version = BuildInfo.version)
    Files.createDirectories(launcher.dataDir)

    val pluginsDir = launcher.dataDir.resolve("plugins")
    BuiltinPluginSync.sync(pluginsDir, logger)

    val manager = PluginManager(launcher, logger)
    val plugins =
        try {
            manager.load(pluginsDir)
        } catch (t: Throwable) {
            logger.error("插件加载失败，启动器将继续以空插件列表运行", t)
            manager.plugins
        }

    logger.info("MMCL {} 启动完成，数据目录：{}", launcher.version, launcher.dataDir)

    application {
        Window(
            onCloseRequest = {
                manager.close()
                exitApplication()
            },
            title = "MMCL ${launcher.version}",
        ) {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LauncherScreen(launcher = launcher, plugins = plugins)
                }
            }
        }
    }
}

@Composable
private fun LauncherScreen(launcher: LauncherInfo, plugins: List<LoadedPlugin>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("MMCL ${launcher.version}", style = MaterialTheme.typography.headlineMedium)
        Text("${launcher.os} / ${launcher.arch}", style = MaterialTheme.typography.bodyMedium)
        Text("数据目录：${launcher.dataDir}", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("插件（${plugins.size}）", style = MaterialTheme.typography.titleMedium)
        if (plugins.isEmpty()) {
            Text("暂无插件", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plugins, key = { it.id }) { plugin ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(plugin.id, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${plugin.version} · ${plugin.descriptor.jar?.fileName ?: "内置"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    AssistChip(onClick = {}, label = { Text(plugin.state.name) })
                }
            }
        }
    }
}
