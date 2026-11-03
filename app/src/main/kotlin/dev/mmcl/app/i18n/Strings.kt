package dev.mmcl.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.util.Properties

/**
 * 文案查找：按顺序查各语言包，都没有时回退到 key 本身。
 *
 * 回退到 key 而不是抛异常或显示空白：漏翻译只是难看，不影响功能。
 */
interface Strings {
    /** 取一条文案。 */
    fun t(key: String): String

    /** 取一条带占位符的文案，参数按 `String.format` 规则填充。 */
    fun t(key: String, vararg args: Any?): String {
        val pattern = t(key)
        return runCatching { String.format(pattern, *args) }.getOrDefault(pattern)
    }
}

/**
 * 由若干 map 组成的查找链；靠前的优先。
 *
 * @param onMissing 缺 key 时的回调，用于"每个 key 只告警一次"
 */
class MapStrings(
    private val chain: List<Map<String, String>>,
    private val onMissing: (String) -> Unit = {},
) : Strings {
    override fun t(key: String): String {
        for (map in chain) {
            map[key]?.let { return it }
        }
        onMissing(key)
        return key
    }
}

/** 当前语言的文案；由 `Main` 注入。 */
val LocalStrings = compositionLocalOf<Strings> { MapStrings(emptyList()) }

/** 组合内取文案的便捷入口。 */
@Composable
fun strings(): Strings = LocalStrings.current

/** 应用内置语言包，来自 `resources/i18n/` 下的 properties 文件。 */
object BuiltinStrings {
    /** 英文（源语言，缺 key 的最终回退）。 */
    val english: Map<String, String> = load("i18n/strings_en.properties")

    /** 简体中文。 */
    val simplifiedChinese: Map<String, String> = load("i18n/strings_zh_CN.properties")

    private fun load(path: String): Map<String, String> =
        runCatching {
            val properties = Properties()
            val stream = BuiltinStrings::class.java.getResourceAsStream("/$path") ?: return emptyMap()
            stream.use { properties.load(it.reader(Charsets.UTF_8)) }
            properties.stringPropertyNames().associateWith { key -> properties.getProperty(key) }
        }.getOrDefault(emptyMap())
}
