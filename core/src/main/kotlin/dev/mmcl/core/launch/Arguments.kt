package dev.mmcl.core.launch

import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.version.OsRule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 启动参数模板求值。
 *
 * `arguments` 里每个元素可能有两种形态：
 * - 纯字符串：直接采用；
 * - 对象：`{ "rules": [...], "value": "字符串" | ["数组"] }`，先按规则判断是否采用。
 *
 * 规则语义与库规则一致：无规则→采用；按顺序匹配，最后命中的规则决定 allow/deny。
 * `features` 目前只支持"全为 false"（demo 用户、自定义分辨率、quickPlay 都不启用），
 * 需要某 feature 为 true 的规则一律不采用。
 */
internal object Arguments {
    /** 求值参数列表，返回展开后的字符串列表。 */
    fun resolve(
        elements: List<JsonElement>,
        os: OperatingSystem,
        features: Map<String, Boolean> = emptyMap(),
    ): List<String> =
        elements.flatMap { element ->
            when (element) {
                is JsonPrimitive -> listOf(element.content)
                is JsonObject -> resolveObject(element, os, features)
                else -> emptyList()
            }
        }

    /** 把 `\${key}` 占位符替换成实际值；未知占位符保持原样并会被记录到调用方。 */
    fun substitute(elements: List<String>, values: Map<String, String>): List<String> =
        elements.map { element ->
            values.entries.fold(element) { acc, (key, value) -> acc.replace("\${$key}", value) }
        }

    private fun resolveObject(element: JsonObject, os: OperatingSystem, features: Map<String, Boolean>): List<String> {
        val rules = element["rules"]?.jsonArray?.map { parseRule(it) } ?: emptyList()
        if (!matches(rules, os, features)) return emptyList()
        val value = element["value"] ?: return emptyList()
        return when (value) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(value.content)
            else -> emptyList()
        }
    }

    private fun parseRule(element: JsonElement): ArgumentRule {
        val obj = element.jsonObject
        val osRule =
            obj["os"]?.let { osElement ->
                val osObj = osElement.jsonObject
                OsRule(
                    name = (osObj["name"] as? JsonPrimitive)?.content,
                    arch = (osObj["arch"] as? JsonPrimitive)?.content,
                )
            }
        val features =
            obj["features"]?.jsonObject?.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.booleanOrNull?.let { key to it }
            }?.toMap().orEmpty()
        return ArgumentRule(
            action = (obj["action"] as? JsonPrimitive)?.content ?: "allow",
            os = osRule,
            features = features,
        )
    }

    private fun matches(rules: List<ArgumentRule>, os: OperatingSystem, features: Map<String, Boolean>): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (!rule.matches(os, features)) continue
            allowed = rule.action != "deny"
        }
        return allowed
    }

    private data class ArgumentRule(
        val action: String,
        val os: OsRule?,
        val features: Map<String, Boolean>,
    ) {
        fun matches(currentOs: OperatingSystem, currentFeatures: Map<String, Boolean>): Boolean {
            val osMatches =
                os == null ||
                    when (os.name?.lowercase()) {
                        null, "" -> true
                        "osx", "macos" -> currentOs == OperatingSystem.MACOS
                        "windows" -> currentOs == OperatingSystem.WINDOWS
                        "linux" -> currentOs == OperatingSystem.LINUX
                        else -> false
                    }
            if (!osMatches) return false
            return features.all { (key, expected) -> (currentFeatures[key] ?: false) == expected }
        }
    }
}
