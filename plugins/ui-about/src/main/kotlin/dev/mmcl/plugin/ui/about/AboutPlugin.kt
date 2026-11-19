package dev.mmcl.plugin.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.LocalLauncherLocale
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.ui.UiSlot
import dev.mmcl.api.ui.UiWidget
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold

/** 「关于」插件里所有会展示给用户的文案；中英各一份，按当前语言选。 */
internal data class AboutTexts(
    val pageTitle: String,
    val headerTitle: String,
    val headerSubtitle: String,
    val launcherVersion: String,
    val platform: String,
    val dataDir: String,
    val pageFooter: String,
    val homeCard: String,
    val statusBadge: String,
)

internal val ABOUT_ENGLISH =
    AboutTexts(
        pageTitle = "About",
        headerTitle = "About MMCL",
        headerSubtitle = "Built-in sample UI plugin",
        launcherVersion = "Launcher version: %s",
        platform = "Platform: %s",
        dataDir = "Data folder: %s",
        pageFooter = "This page is registered by a plugin through UiPage, proving plugins can append pages to the left navigation.",
        homeCard = "This card is registered by the ui-about plugin through UiWidget, proving the home slot works.",
        statusBadge = "about plugin",
    )

internal val ABOUT_CHINESE =
    AboutTexts(
        pageTitle = "关于",
        headerTitle = "关于 MMCL",
        headerSubtitle = "内置示例 UI 插件",
        launcherVersion = "启动器版本：%s",
        platform = "运行平台：%s",
        dataDir = "数据目录：%s",
        pageFooter = "这一页由插件通过 UiPage 注册，用来验证插件可以往左侧导航栏追加页面。",
        homeCard = "这张卡片由 ui-about 插件通过 UiWidget 注册，用来验证首页插槽。",
        statusBadge = "about 插件",
    )

/** 按语言标签选文案；以 `zh` 开头用中文，其余用英文。 */
internal fun aboutTexts(locale: String): AboutTexts =
    if (locale.lowercase().startsWith("zh")) ABOUT_CHINESE else ABOUT_ENGLISH

/**
 * 示例 UI 页面：演示插件如何往导航栏加一页（`dev.mmcl.api.ui.UiPage`），
 * 并演示如何跟随启动器语言（`LocalLauncherLocale`）。
 */
class AboutPage(private val launcher: LauncherInfo) : UiPage {
    override val id: String = "dev.mmcl.ui.about"
    override val title: String = "关于"
    override val icon: UiIcon = UiIcon.INFO
    override val group: UiGroup = UiGroup.TOOLS
    override val order: Int = 200

    @Composable
    override fun LocalizedTitle(): String = aboutTexts(LocalLauncherLocale.current).pageTitle

    @Composable
    override fun Content(navigator: AppNavigator) {
        val texts = aboutTexts(LocalLauncherLocale.current)
        PageScaffold(title = texts.headerTitle, subtitle = texts.headerSubtitle) {
            Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.xs)) {
                Text(text = texts.launcherVersion.format(launcher.version), style = MaterialTheme.typography.bodyMedium)
                Text(text = texts.platform.format("${launcher.os} / ${launcher.arch}"), style = MaterialTheme.typography.bodyMedium)
                Text(text = texts.dataDir.format(launcher.dataDir), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = texts.pageFooter,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 注册 [AboutPage] 的插件入口。 */
class AboutPlugin : Plugin {
    override val id: String = "dev.mmcl.ui.about.plugin"
    override val version: String = "0.0.2"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val title: String
        get() = if (isChinese(localeProvider())) "关于页面" else "About page"
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.UI

    /** `onLoad` 之前不读语言，先按英文兜底。 */
    private var localeProvider: () -> String = { ENGLISH }

    override fun onLoad(context: PluginContext) {
        localeProvider = { context.locale }
        context.services.register(UiPage::class, AboutPage(context.launcher))
        context.services.register(UiWidget::class, AboutHomeWidget(context.launcher))
        context.services.register(UiWidget::class, AboutStatusWidget())
        context.logger.info("关于页与插槽小部件已注册（当前语言 {}）", context.locale)
    }
}

/** 首页卡片：演示插件往 [UiSlot.HOME] 挂内容。 */
class AboutHomeWidget(private val launcher: LauncherInfo) : UiWidget {
    override val id: String = "dev.mmcl.ui.about.home"
    override val slot: UiSlot = UiSlot.HOME
    override val order: Int = 100

    @Composable
    override fun Content(navigator: AppNavigator) {
        val texts = aboutTexts(LocalLauncherLocale.current)
        Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.xs)) {
            Text(text = "MMCL ${launcher.version}", style = MaterialTheme.typography.titleSmall)
            Text(
                text = texts.homeCard,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 状态栏标记：演示插件往 [UiSlot.STATUS_BAR] 挂内容。 */
class AboutStatusWidget : UiWidget {
    override val id: String = "dev.mmcl.ui.about.status"
    override val slot: UiSlot = UiSlot.STATUS_BAR
    override val order: Int = 100

    @Composable
    override fun Content(navigator: AppNavigator) {
        Text(
            text = aboutTexts(LocalLauncherLocale.current).statusBadge,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 缺省语言：`onLoad` 之前用英文兜底。 */
private const val ENGLISH = "en"

/** 界面语言是否为中文；插件展示名跟随 `PluginContext.locale`，不写死。 */
private fun isChinese(locale: String): Boolean = locale.lowercase().startsWith("zh")
