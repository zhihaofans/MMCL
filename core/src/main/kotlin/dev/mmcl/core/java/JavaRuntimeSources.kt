package dev.mmcl.core.java

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.JavaRuntimeProvider
import dev.mmcl.api.capability.RuntimeDownload
import dev.mmcl.api.service.SettingsService
import kotlinx.coroutines.CancellationException

/**
 * Java 运行时来源的选择与排序。
 *
 * 规则与版本清单源一致（见 `VersionManifestService.orderedSources`）：
 * 1. 设置项 [SETTING_KEY] 显式指定的来源排最前；值为空或 `auto` 表示不指定；
 * 2. 其余按 `priority` 升序、`id` 升序。
 *
 * 这里只决定「下载 Java」时用哪个来源；已经装好的运行时仍由 [JavaRuntimeDetector]
 * 按「手动指定 → 自带 runtime → JAVA_HOME → PATH」的顺序探测。
 *
 * @param providers 可用来源；每次求值，插件可能随时注册
 * @param settings 读取/保存用户指定来源；为 `null` 时只按优先级排序
 */
class JavaRuntimeSources(
    private val providers: () -> List<JavaRuntimeProvider>,
    private val settings: SettingsService? = null,
) {
    /** 全部来源，按优先级排序（不受用户选择影响），供设置页展示。 */
    fun all(): List<JavaRuntimeProvider> =
        providers().sortedWith(compareBy({ it.priority }, { it.id }))

    /**
     * 按「实际会用谁」的顺序返回：用户选中的排最前，其余按优先级。
     */
    fun ordered(): List<JavaRuntimeProvider> {
        val sorted = all()
        val preferred = selectedId() ?: return sorted
        val picked = sorted.firstOrNull { it.id == preferred } ?: return sorted
        return listOf(picked) + sorted.filterNot { it.id == picked.id }
    }

    /** 当前会用于下载的来源；没有任何来源插件时为 `null`。 */
    fun current(): JavaRuntimeProvider? = ordered().firstOrNull()

    /**
     * 按顺序问每个来源要下载信息，返回第一个能给出的。
     *
     * 镜像站经常缺某个平台或某个大版本（例如清华的 Windows 包更新比 Linux 慢），
     * 单个来源返回 `null` 或抛异常时自动换下一个，不让用户卡在"该来源不支持"上。
     *
     * @return 来源与下载信息；所有来源都不支持时返回 `null`
     */
    suspend fun resolve(
        majorVersion: Int,
        os: OperatingSystem,
        arch: Architecture,
    ): Pair<JavaRuntimeProvider, RuntimeDownload>? {
        for (provider in ordered()) {
            val download =
                try {
                    provider.resolve(majorVersion, os, arch)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    null
                }
            if (download != null) return provider to download
        }
        return null
    }

    /**
     * 用户显式选中、且当前确实存在的来源 id；没选、选了 `auto`，或选中的插件已被卸载时返回 `null`。
     *
     * 返回"有效选择"而不是原始设置值：设置页据此决定哪个单选项是亮的，插件被删掉后不会留下悬空选中。
     */
    fun selectedId(): String? {
        val raw = settings?.getString(SETTING_KEY)?.takeIf { it.isNotBlank() && it != AUTO } ?: return null
        return raw.takeIf { wanted -> providers().any { it.id == wanted } }
    }

    /** 记住用户的选择；传 [AUTO] 表示恢复按优先级自动选。 */
    fun select(providerId: String) {
        settings?.putString(SETTING_KEY, providerId)
    }

    /** 是否至少装了一个运行时来源插件；界面用它决定显示"去装插件"还是可选项。 */
    fun hasProvider(): Boolean = providers().isNotEmpty()

    companion object {
        /** 设置键：指定 Java 运行时来源 id，[AUTO] 表示按优先级自动选。 */
        const val SETTING_KEY: String = "java.runtime.provider"

        /** [SETTING_KEY] 的自动取值。 */
        const val AUTO: String = "auto"
    }
}
