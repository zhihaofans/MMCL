package dev.mmcl.core.launch

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameLogFormatterTest {
    private val zone = ZoneId.of("UTC")
    private val timestamp = Instant.parse("2026-10-31T12:34:56Z").toEpochMilli()
    private val formatter = GameLogFormatter(zone)

    private fun event(level: String, thread: String, logger: String, message: String, throwable: String? = null): List<String> =
        buildList {
            add("""  <log4j:Event logger="$logger" timestamp="$timestamp" level="$level" thread="$thread">""")
            add("""    <log4j:Message><![CDATA[$message]]></log4j:Message>""")
            if (throwable != null) {
                add("""    <log4j:Throwable><![CDATA[$throwable]]></log4j:Throwable>""")
            }
            add("  </log4j:Event>")
        }

    private fun format(vararg lines: String): List<String> =
        lines.flatMap { formatter.accept(it) } + formatter.finish()

    @Test
    fun `XML 事件合并成一行`() {
        val lines =
            format(
                *event(
                    level = "INFO",
                    thread = "Render thread",
                    logger = "net.minecraft.client.Minecraft",
                    message = "Setting user: Steve",
                ).toTypedArray(),
            )

        assertEquals(listOf("[12:34:56] [Render thread/INFO] [Minecraft] Setting user: Steve"), lines)
    }

    @Test
    fun `异常堆栈保留并缩进`() {
        val throwable = "java.lang.RuntimeException: boom\n\tat a.b.C.method(C.kt:1)\n\tat d.E.run(E.kt:2)"

        val lines =
            format(
                *event(
                    level = "ERROR",
                    thread = "Download-2",
                    logger = "net.minecraft.client.Minecraft",
                    message = "Failed to fetch user properties",
                    throwable = throwable,
                ).toTypedArray(),
            )

        assertEquals("[12:34:56] [Download-2/ERROR] [Minecraft] Failed to fetch user properties", lines.first())
        assertTrue(lines.any { it.trim().startsWith("java.lang.RuntimeException: boom") })
        assertTrue(lines.any { it.contains("[C.kt:1]") || it.contains("a.b.C.method") })
    }

    @Test
    fun `普通文本原样透传`() {
        assertEquals(listOf("hello", "world"), format("hello", "world"))
    }

    @Test
    fun `未闭合的事件在 finish 时输出`() {
        val formatter = GameLogFormatter(zone)
        val emitted =
            formatter.accept("""<log4j:Event logger="a.B" timestamp="$timestamp" level="INFO" thread="main">""") +
                formatter.accept("""  <log4j:Message><![CDATA[half written]]></log4j:Message>""") +
                formatter.finish()

        assertEquals(listOf("[12:34:56] [main/INFO] [B] half written"), emitted)
    }

    @Test
    fun `CDATA 里的转义碎片会被截断`() {
        val lines =
            format(
                *event(
                    level = "INFO",
                    thread = "Datafixer Bootstrap #0",
                    logger = "com.mojang.datafixers.DataFixerBuilder",
                    message = "307 Datafixer optimizations took 170 milliseconds]]]]><![CDATA[>",
                ).toTypedArray(),
            )

        assertEquals(
            listOf("[12:34:56] [Datafixer Bootstrap #0/INFO] [DataFixerBuilder] 307 Datafixer optimizations took 170 milliseconds"),
            lines,
        )
    }

    @Test
    fun `缺少 message 的事件退回原始块`() {
        val formatter = GameLogFormatter(zone)
        val raw = """<log4j:Event logger="x.Y" timestamp="$timestamp" level="INFO" thread="main">"""
        val emitted = formatter.accept(raw) + formatter.accept("  </log4j:Event>") + formatter.finish()

        assertTrue(emitted.any { it.contains("<log4j:Event") }, "解析失败时应保留原始内容：$emitted")
    }
}
