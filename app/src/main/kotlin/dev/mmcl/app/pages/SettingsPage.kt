package dev.mmcl.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.service.SettingsService
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.LauncherTheme
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.capability.JavaRuntimeProvider
import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.app.theme.ThemeController
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import dev.mmcl.app.appearance.AppearanceStore
import dev.mmcl.app.appearance.loadBackgroundBitmap
import dev.mmcl.app.appearance.loadFontFamily
import dev.mmcl.core.java.JavaRuntimeSources
import dev.mmcl.core.platform.DirectoryLauncher
import dev.mmcl.core.version.VersionManifestService
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/**
 * 设置页：主题切换（即时生效并持久化）+ 运行信息。
 *
 * 主题列表 = 内置主题 + 插件注册的主题；点击即调用 [ThemeController.apply]。
 */
class SettingsPage(
    private val launcher: LauncherInfo,
    private val themeController: ThemeController,
    private val safeMode: Boolean,
    private val manifests: VersionManifestService? = null,
    private val settings: SettingsService? = null,
    private val javaSources: JavaRuntimeSources? = null,
    private val appearance: AppearanceStore? = null,
    private val onFontFamilyChanged: (FontFamily?) -> Unit = {},
    private val onBackgroundChanged: (ImageBitmap?) -> Unit = {},
) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "设置"
    override val icon: UiIcon = UiIcon.SETTINGS
    override val group: UiGroup = UiGroup.SYSTEM
    override val order: Int = 30

    /** 平台相关的"打开目录"实现；目录不存在时会先建出来。 */
    private val directoryLauncher = DirectoryLauncher(launcher.os)

    /** 插件目录：内置插件由 `BuiltinPluginSync` 同步到这里，用户插件也放这里。 */
    private val pluginsDir: Path
        get() = launcher.dataDir.resolve("plugins")

    companion object {
        /** 页面 id。 */
        const val PAGE_ID: String = "dev.mmcl.page.settings"

        /** 下载限速设置键（KB/s，0 = 不限）。 */
        const val LIMIT_KEY: String = "download.maxKbPerSecond"
    }

    @Composable
    override fun Content(navigator: AppNavigator) {
        val scope = rememberCoroutineScope()
        PageScaffold(title = "设置", subtitle = "外观与运行信息") {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
            ) {
                SectionTitle("外观")
                val currentId = themeController.current.id
                themeController.all().forEach { theme ->
                    ThemeRow(theme = theme, selected = theme.id == currentId) { themeController.apply(theme.id) }
                }
                if (safeMode) {
                    Text(
                        text = "安全模式已开启：已跳过全部插件主题与插件页面。去掉 --safe-mode 与 MMCL_SAFE_MODE 后重启即可恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = MmclSpacing.s),
                    )
                }

                var fontLabel by remember(appearance) {
                    mutableStateOf(appearance?.fontFile()?.fileName?.toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = MmclSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("字体", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = fontLabel ?: "跟随系统默认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            val picked = chooseFontFile() ?: return@Button
                            appearance?.setFontFile(picked)
                            val family = loadFontFamily(picked)
                            onFontFamilyChanged(family)
                            fontLabel =
                                if (family != null) picked.fileName.toString() else "字体无效（已退回默认）"
                        },
                        enabled = appearance != null,
                    ) { Text("选择…") }
                    if (fontLabel != null) {
                        TextButton(
                            onClick = {
                                appearance?.setFontFile(null)
                                onFontFamilyChanged(null)
                                fontLabel = null
                            },
                        ) { Text("恢复默认") }
                    }
                }
                Text(
                    text = "支持 .ttf / .otf / .ttc；换字体后界面立即重排，不用重启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var backgroundLabel by remember(appearance) {
                    mutableStateOf(appearance?.backgroundImage()?.fileName?.toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = MmclSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("背景图", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = backgroundLabel ?: "跟随主题（无背景图）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            val picked = chooseImageFile() ?: return@Button
                            appearance?.setBackgroundImage(picked)
                            val bitmap = loadBackgroundBitmap(picked)
                            onBackgroundChanged(bitmap)
                            backgroundLabel =
                                if (bitmap != null) picked.fileName.toString() else "图片无效（已退回主题）"
                        },
                        enabled = appearance != null,
                    ) { Text("选择…") }
                    if (backgroundLabel != null) {
                        TextButton(
                            onClick = {
                                appearance?.setBackgroundImage(null)
                                onBackgroundChanged(null)
                                backgroundLabel = null
                            },
                        ) { Text("清除") }
                    }
                }
                Text(
                    text = "支持 .png / .jpg / .jpeg / .webp；铺满窗口，面板会变半透明保证文字可读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle("下载源")
                val sources = manifests?.orderedSources().orEmpty()
                if (sources.isEmpty()) {
                    Text(
                        text = "未安装下载源插件。本应用不内置任何下载地址：把下载源插件放进 <数据目录>/plugins/ 后重启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    var selectedSourceId by remember(manifests) { mutableStateOf(sources.first().id) }
                    for (source in sources) {
                        SourceRow(
                            source = source,
                            selected = source.id == selectedSourceId,
                            onClick = {
                                selectedSourceId = source.id
                                manifests?.selectSource(source.id)
                            },
                        )
                    }
                    Text(
                        text = "缓存来源：${manifests?.cachedSourceId() ?: "无"}；选中后下一次刷新生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle("Java 运行时来源")
                val providers = javaSources?.all().orEmpty()
                if (providers.isEmpty()) {
                    Text(
                        text = "未安装 Java 运行时来源插件。缺少 Java 时，装一个运行时来源插件" +
                            "（如 runtime-adoptium / runtime-foojay）再重启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    var selectedJavaSourceId by remember(javaSources) {
                        mutableStateOf(javaSources?.selectedId())
                    }
                    ChoiceRow(
                        title = "自动",
                        subtitle = "按优先级依次尝试，镜像缺包时自动换下一个",
                        selected = selectedJavaSourceId == null,
                        onClick = {
                            selectedJavaSourceId = null
                            javaSources?.select(JavaRuntimeSources.AUTO)
                        },
                    )
                    for (provider in providers) {
                        JavaSourceRow(
                            provider = provider,
                            selected = provider.id == selectedJavaSourceId,
                            onClick = {
                                selectedJavaSourceId = provider.id
                                javaSources?.select(provider.id)
                            },
                        )
                    }
                    Text(
                        text = "下载 Java 时用选中的来源；不选则按优先级自动挑。" +
                            "已装好的运行时仍按「手动指定 → 自带 runtime → JAVA_HOME → PATH」探测。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle("插件")
                var pluginDirMessage by remember { mutableStateOf<String?>(null) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { directoryLauncher.open(pluginsDir) }
                                pluginDirMessage =
                                    result.fold(
                                        onSuccess = { "已在文件管理器中打开" },
                                        onFailure = { "打开失败：${it.message ?: it::class.simpleName}" },
                                    )
                            }
                        },
                    ) { Text("打开插件目录") }
                    Text(
                        text = pluginsDir.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                pluginDirMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle("下载")
                var limitText by remember(settings) {
                    mutableStateOf(settings?.getString(LIMIT_KEY)?.takeIf { it.isNotBlank() } ?: "0")
                }
                var limitSaved by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
                ) {
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { value ->
                            limitText = value.filter { it.isDigit() }
                            limitSaved = false
                        },
                        label = { Text("限速（KB/s，0 = 不限）") },
                        singleLine = true,
                        modifier = Modifier.width(240.dp),
                    )
                    Button(
                        onClick = {
                            settings?.putString(LIMIT_KEY, limitText.ifBlank { "0" })
                            limitSaved = true
                        },
                        enabled = settings != null,
                    ) { Text(if (limitSaved) "已保存" else "保存") }
                }
                Text(
                    text = "所有并发下载共享这个总速率；改动在下次下载开始时生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle("关于")
                InfoRow("版本", launcher.version)
                InfoRow("平台", "${launcher.os} / ${launcher.arch}")
                InfoRow("数据目录", launcher.dataDir.toString())
            }
        }
    }
}

@Composable
private fun SourceRow(source: VersionManifestSource, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MmclSpacing.s, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.xs),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${source.id} · 优先级 ${source.priority}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JavaSourceRow(provider: JavaRuntimeProvider, selected: Boolean, onClick: () -> Unit) {
    ChoiceRow(
        title = provider.displayName,
        subtitle = "${provider.id} · 优先级 ${provider.priority}",
        selected = selected,
        onClick = onClick,
    )
}

/** 单选行：自动选项与具体来源共用。 */
@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MmclSpacing.s, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.xs),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = MmclSpacing.m),
    )
}

@Composable
private fun ThemeRow(theme: LauncherTheme, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MmclSpacing.s, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.xs),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = theme.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = theme.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (theme.dark) "深色" else "浅色",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.m),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(84.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 选一个字体文件；取消或无图形界面时返回 null。 */
private fun chooseFontFile(): Path? =
    runCatching {
        val dialog = FileDialog(null as Frame?, "选择字体文件", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            AppearanceStore.SUPPORTED_EXTENSIONS.any { lower.endsWith(".\$it") }
        }
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        if (directory == null || file == null) null else Path.of(directory, file)
    }.getOrNull()

/** 选一张背景图；取消或无图形界面时返回 null。 */
private fun chooseImageFile(): Path? =
    runCatching {
        val dialog = FileDialog(null as Frame?, "选择背景图", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            AppearanceStore.SUPPORTED_IMAGE_EXTENSIONS.any { lower.endsWith(".\$it") }
        }
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        if (directory == null || file == null) null else Path.of(directory, file)
    }.getOrNull()
