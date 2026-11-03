package dev.mmcl.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.ui.UiSlot
import dev.mmcl.api.ui.UiWidget
import dev.mmcl.core.download.DownloadTask
import dev.mmcl.core.download.DownloadsService
import dev.mmcl.ui.kit.MmclIcons
import dev.mmcl.ui.kit.MmclSpacing

/**
 * 启动器外壳：顶栏 + 左侧导航 + 内容区 + 状态栏（`UI_DESIGN.md` §2）。
 *
 * 布局骨架固定，不允许插件修改；插件只能通过 [pages] 增加页面。
 */
@Composable
fun AppShell(
    pages: List<UiPage>,
    launcher: LauncherInfo,
    safeMode: Boolean,
    pluginCount: Int,
    downloads: DownloadsService? = null,
    widgets: List<UiWidget> = emptyList(),
    titleBarInset: Dp = 0.dp,
) {
    val navigator = remember { NavigationState(pages.first().id) }
    val current = pages.firstOrNull { it.id == navigator.currentPageId } ?: pages.first()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthClass = shellWidthClass(maxWidth)
        Column(modifier = Modifier.fillMaxSize()) {
        val widgetsBySlot = widgets.groupBy { it.slot }
        TopBar(
            title = current.title,
            launcher = launcher,
            safeMode = safeMode,
            canGoBack = navigator.canGoBack(),
            onBack = { navigator.back() },
            widgets = widgetsBySlot[UiSlot.TOP_BAR].orEmpty(),
            navigator = navigator,
            insetStart = titleBarInset,
        )
        HorizontalDivider()
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            NavRail(
                widthClass = widthClass,
                pages = pages,
                currentPageId = current.id,
            ) { navigator.select(it) }
            Divider()
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                current.Content(navigator)
            }
        }
        HorizontalDivider()
        val downloadTasks: List<DownloadTask> = downloads?.tasks?.collectAsState()?.value.orEmpty()
        StatusBar(
            launcher = launcher,
            pluginCount = pluginCount,
            pageCount = pages.size,
            downloadTasks = downloadTasks,
            widgets = widgetsBySlot[UiSlot.STATUS_BAR].orEmpty(),
            navigator = navigator,
        )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    launcher: LauncherInfo,
    safeMode: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    widgets: List<UiWidget> = emptyList(),
    navigator: AppNavigator? = null,
    insetStart: Dp = 0.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = MmclSpacing.l + insetStart, end = MmclSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s),
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MmclIcons.of(UiIcon.BACK),
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.weight(1f))
        if (navigator != null) {
            widgets.forEach { widget -> widget.Content(navigator) }
        }
        if (safeMode) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(percent = 50),
            ) {
                Text(
                    text = "安全模式",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Text(
            text = "v${launcher.version}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavRail(
    widthClass: ShellWidthClass,
    pages: List<UiPage>,
    currentPageId: String,
    onSelect: (String) -> Unit,
) {
    val railWidth =
        if (widthClass == ShellWidthClass.COMPACT) ShellMetrics.RAIL_WIDTH_COMPACT else ShellMetrics.RAIL_WIDTH_FULL
    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.onWallpaper())
            .padding(vertical = MmclSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MmclSpacing.xs),
    ) {
        var rendered = false
        for (group in UiGroup.entries) {
            val groupPages = pages.filter { it.group == group }
            if (groupPages.isEmpty()) continue
            if (rendered) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = MmclSpacing.m, vertical = MmclSpacing.xs))
            }
            rendered = true
            for (page in groupPages) {
                RailItem(
                    page = page,
                    selected = page.id == currentPageId,
                    showLabel = widthClass != ShellWidthClass.COMPACT,
                    onClick = { onSelect(page.id) },
                )
            }
        }
    }
}

@Composable
private fun RailItem(page: UiPage, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MmclSpacing.xs)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = MmclIcons.of(page.icon),
            contentDescription = page.title,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        if (showLabel) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun StatusBar(
    launcher: LauncherInfo,
    pluginCount: Int,
    pageCount: Int,
    downloadTasks: List<DownloadTask> = emptyList(),
    widgets: List<UiWidget> = emptyList(),
    navigator: AppNavigator? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.onWallpaper())
            .padding(horizontal = MmclSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmclSpacing.l),
    ) {
        Text(
            text = "${launcher.os} / ${launcher.arch}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "插件 $pluginCount · 页面 $pageCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (navigator != null) {
            widgets.forEach { widget -> widget.Content(navigator) }
        }
        val active = downloadTasks.firstOrNull { it.state.isActive }
        if (active != null) {
            val progress = active.progress
            Text(
                text = if (progress != null) {
                    "下载 ${progress.completed}/${progress.total}"
                } else {
                    "下载 ${active.state.name.lowercase()}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "MMCL v${launcher.version}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
