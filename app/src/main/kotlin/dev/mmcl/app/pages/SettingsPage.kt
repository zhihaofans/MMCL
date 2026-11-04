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
import dev.mmcl.app.i18n.LocaleController
import dev.mmcl.app.i18n.LocalStrings
import dev.mmcl.app.i18n.Strings
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
    private val localeController: LocaleController? = null,
    private val onLocaleChanged: (Strings) -> Unit = {},
) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "设置"
    override val titleKey: String = "page.settings.title"
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
        val strings = LocalStrings.current
        PageScaffold(title = strings.t("settings.title"), subtitle = strings.t("settings.subtitle")) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MmclSpacing.s),
            ) {
                SectionTitle(strings.t("settings.section.appearance"))
                val currentId = themeController.current.id
                themeController.all().forEach { theme ->
                    ThemeRow(theme = theme, selected = theme.id == currentId) { themeController.apply(theme.id) }
                }
                if (safeMode) {
                    Text(
                        text = strings.t("settings.safe_mode"),
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
                        Text(strings.t("settings.font.title"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = fontLabel ?: strings.t("settings.font.default"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            val picked = chooseFontFile(strings.t("settings.font.dialog")) ?: return@Button
                            appearance?.setFontFile(picked)
                            val family = loadFontFamily(picked)
                            onFontFamilyChanged(family)
                            fontLabel =
                                if (family != null) picked.fileName.toString() else strings.t("settings.font.invalid")
                        },
                        enabled = appearance != null,
                    ) { Text(strings.t("settings.font.choose")) }
                    if (fontLabel != null) {
                        TextButton(
                            onClick = {
                                appearance?.setFontFile(null)
                                onFontFamilyChanged(null)
                                fontLabel = null
                            },
                        ) { Text(strings.t("settings.font.reset")) }
                    }
                }
                Text(
                    text = strings.t("settings.font.hint"),
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
                        Text(strings.t("settings.background.title"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = backgroundLabel ?: strings.t("settings.background.default"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            val picked = chooseImageFile(strings.t("settings.background.dialog")) ?: return@Button
                            appearance?.setBackgroundImage(picked)
                            val bitmap = loadBackgroundBitmap(picked)
                            onBackgroundChanged(bitmap)
                            backgroundLabel =
                                if (bitmap != null) picked.fileName.toString() else strings.t("settings.background.invalid")
                        },
                        enabled = appearance != null,
                    ) { Text(strings.t("settings.background.choose")) }
                    if (backgroundLabel != null) {
                        TextButton(
                            onClick = {
                                appearance?.setBackgroundImage(null)
                                onBackgroundChanged(null)
                                backgroundLabel = null
                            },
                        ) { Text(strings.t("settings.background.clear")) }
                    }
                }
                Text(
                    text = strings.t("settings.background.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle(strings.t("settings.language.title"))
                val selectedLocale = localeController?.selected() ?: LocaleController.AUTO
                ChoiceRow(
                    title = strings.t("settings.language.auto"),
                    subtitle = localeController?.systemLocale().orEmpty(),
                    selected = selectedLocale == LocaleController.AUTO,
                    onClick = {
                        localeController?.select(LocaleController.AUTO)
                        localeController?.let { onLocaleChanged(it.strings()) }
                    },
                )
                localeController?.options()?.forEach { option ->
                    ChoiceRow(
                        title = option.displayName,
                        subtitle = option.locale,
                        selected = selectedLocale.equals(option.locale, ignoreCase = true),
                        onClick = {
                            localeController.select(option.locale)
                            onLocaleChanged(localeController.strings())
                        },
                    )
                }
                Text(
                    text = strings.t("settings.language.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle(strings.t("settings.section.sources"))
                val sources = manifests?.orderedSources().orEmpty()
                if (sources.isEmpty()) {
                    Text(
                        text = strings.t("settings.sources.empty"),
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
                        text = strings.t("settings.sources.cached", manifests?.cachedSourceId() ?: strings.t("settings.sources.none")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle(strings.t("settings.section.java"))
                val providers = javaSources?.all().orEmpty()
                if (providers.isEmpty()) {
                    Text(
                        text = strings.t("settings.java.empty"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    var selectedJavaSourceId by remember(javaSources) {
                        mutableStateOf(javaSources?.selectedId())
                    }
                    ChoiceRow(
                        title = strings.t("settings.java.auto"),
                        subtitle = strings.t("settings.java.auto_hint"),
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
                        text = strings.t("settings.java.hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle(strings.t("settings.section.plugins"))
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
                                        onSuccess = { strings.t("settings.plugins.opened") },
                                        onFailure = { strings.t("settings.plugins.open_failed", it.message ?: it::class.simpleName) },
                                    )
                            }
                        },
                    ) { Text(strings.t("settings.plugins.open")) }
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

                SectionTitle(strings.t("settings.section.download"))
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
                        label = { Text(strings.t("settings.download.limit")) },
                        singleLine = true,
                        modifier = Modifier.width(240.dp),
                    )
                    Button(
                        onClick = {
                            settings?.putString(LIMIT_KEY, limitText.ifBlank { "0" })
                            limitSaved = true
                        },
                        enabled = settings != null,
                    ) { Text(if (limitSaved) strings.t("settings.download.saved") else strings.t("settings.download.save")) }
                }
                Text(
                    text = strings.t("settings.download.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle(strings.t("settings.section.about"))
                InfoRow(strings.t("settings.about.version"), launcher.version)
                InfoRow(strings.t("settings.about.platform"), "${launcher.os} / ${launcher.arch}")
                InfoRow(strings.t("settings.about.data_dir"), launcher.dataDir.toString())
            }
        }
    }
}

@Composable
private fun SourceRow(source: VersionManifestSource, selected: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
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
            source.attribution?.let { attribution ->
                Text(
                    text = attribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = strings.t("settings.priority", source.id, source.priority),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JavaSourceRow(provider: JavaRuntimeProvider, selected: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    ChoiceRow(
        title = provider.displayName,
        subtitle = strings.t("settings.priority", provider.id, provider.priority),
        selected = selected,
        onClick = onClick,
    )
}

/** 单选行：自动选项与具体来源共用。 */
@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
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
    val strings = LocalStrings.current
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
            Text(
                text = when (theme.id) {
                    "builtin.dark" -> strings.t("settings.theme.builtin_dark")
                    "builtin.light" -> strings.t("settings.theme.builtin_light")
                    else -> theme.displayName
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = theme.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = strings.t(if (theme.dark) "settings.theme.dark" else "settings.theme.light"),
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
private fun chooseFontFile(title: String): Path? =
    runCatching {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
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
private fun chooseImageFile(title: String): Path? =
    runCatching {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            AppearanceStore.SUPPORTED_IMAGE_EXTENSIONS.any { lower.endsWith(".\$it") }
        }
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        if (directory == null || file == null) null else Path.of(directory, file)
    }.getOrNull()
