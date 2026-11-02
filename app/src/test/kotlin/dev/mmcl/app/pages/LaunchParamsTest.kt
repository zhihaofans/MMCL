package dev.mmcl.app.pages

import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchParamsTest {
    @Test
    fun `JVM 参数按空白拆分并忽略多余空格`() {
        assertEquals(emptyList(), splitJvmArgs(""))
        assertEquals(emptyList(), splitJvmArgs("   "))
        assertEquals(
            listOf("-Xss2m", "-XX:+UseG1GC", "-Dfoo=bar", "baz"),
            splitJvmArgs("  -Xss2m   -XX:+UseG1GC\t-Dfoo=bar baz "),
        )
    }

    @Test
    fun `环境变量逐行解析，忽略空行注释与没有等号的行`() {
        val raw =
            """
            # 注释
            JAVA_TOOL_OPTIONS=-Dx=1

            NO_EQUALS_LINE
            PATH_EXTRA=/opt/foo
            """.trimIndent()

        assertEquals(
            mapOf("JAVA_TOOL_OPTIONS" to "-Dx=1", "PATH_EXTRA" to "/opt/foo"),
            parseEnvVars(raw),
        )
    }

    @Test
    fun `等号后面的值里可以有等号`() {
        assertEquals(
            mapOf("A" to "b=c"),
            parseEnvVars("A=b=c"),
        )
    }
}
