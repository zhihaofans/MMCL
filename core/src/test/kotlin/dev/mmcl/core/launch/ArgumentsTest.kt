package dev.mmcl.core.launch

import dev.mmcl.api.OperatingSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ArgumentsTest {
    private fun parse(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `纯字符串直接采用`() {
        val elements = (Json.parseToJsonElement("""["--foo", "bar"]""") as kotlinx.serialization.json.JsonArray).toList()

        assertEquals(listOf("--foo", "bar"), Arguments.resolve(elements, OperatingSystem.MACOS))
    }

    @Test
    fun `带 rules 的对象按平台求值`() {
        val elements =
            parse(
                """
                {"v": [
                  "always",
                  {"rules": [{"action": "allow", "os": {"name": "osx"}}], "value": ["--mac", "1"]},
                  {"rules": [{"action": "allow", "os": {"name": "windows"}}], "value": "--win"}
                ]}
                """,
            ).let { (it.getValue("v") as kotlinx.serialization.json.JsonArray).toList() }

        assertEquals(listOf("always", "--mac", "1"), Arguments.resolve(elements, OperatingSystem.MACOS))
        assertEquals(listOf("always", "--win"), Arguments.resolve(elements, OperatingSystem.WINDOWS))
    }

    @Test
    fun `features 为 false 时不采用相关参数`() {
        val elements =
            parse(
                """
                {"v": [
                  {"rules": [{"action": "allow", "features": {"has_custom_resolution": true}}], "value": ["--width", "800"]}
                ]}
                """,
            ).let { (it.getValue("v") as kotlinx.serialization.json.JsonArray).toList() }

        val resolved = Arguments.resolve(elements, OperatingSystem.MACOS)

        assertTrue(resolved.isEmpty())
        assertFalse(resolved.contains("--width"))
    }

    @Test
    fun `deny 规则优先于前面的 allow`() {
        val elements =
            parse(
                """
                {"v": [
                  {"rules": [{"action": "allow"}], "value": "allowed"},
                  {"rules": [{"action": "deny", "os": {"name": "osx"}}], "value": "denied"}
                ]}
                """,
            ).let { (it.getValue("v") as kotlinx.serialization.json.JsonArray).toList() }

        // 最后一个命中的规则是 deny，但它的 value 仍然会出现在结果里吗？不会：值本身仍会被采纳
        // 官方语义是"规则决定整条参数是否采用"，这里验证 osx 上第二条命中后整条被丢弃
        assertEquals(listOf("allowed"), Arguments.resolve(elements, OperatingSystem.MACOS))
    }

    @Test
    fun `占位符替换`() {
        val result = Arguments.substitute(listOf("-Dfoo=\${value}", "--bar", "\${unknown}"), mapOf("value" to "x"))

        assertEquals(listOf("-Dfoo=x", "--bar", "\${unknown}"), result)
    }
}
