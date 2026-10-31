package dev.mmcl.core.launch

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 把 Minecraft 的 log4j2 **XML 事件流**转成可读单行。
 *
 * 游戏（26.x）默认把日志以 `<log4j:Event>` 形式输出，一个事件占 4~6 行，直接展示很难读。
 * 这里按块缓冲：遇到 `<log4j:Event ...>` 开始收集，遇到 `</log4j:Event>` 解析成：
 *
 * ```
 * [12:34:56] [Render thread/INFO] [Minecraft] Setting user: Steve
 *   java.lang.RuntimeException: ...
 * ```
 *
 * - 非 XML 行（普通 stdout/错误）原样透传；
 * - 解析失败时退回原始块，不丢日志；
 * - `latest.log` 仍然写原始内容，只有界面走这里。
 */
class GameLogFormatter(
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val buffer = mutableListOf<String>()
    private var capturing = false

    /** 输入一行原始输出，返回可以展示的行（可能 0..n 行）。 */
    fun accept(line: String): List<String> {
        if (!capturing) {
            if (!line.contains(OPEN_TAG)) return listOf(line)
            capturing = true
            buffer += line
            if (line.contains(CLOSE_TAG)) return flushBlock()
            return emptyList()
        }
        buffer += line
        if (line.contains(CLOSE_TAG) || buffer.size > MAX_BLOCK_LINES) return flushBlock()
        return emptyList()
    }

    /** 流结束时把未闭合的事件也输出，避免最后一段日志丢失。 */
    fun finish(): List<String> = if (capturing) flushBlock() else emptyList()

    private fun flushBlock(): List<String> {
        val block = buffer.toList()
        buffer.clear()
        capturing = false
        return runCatching { format(block) }.getOrElse { block }
    }

    private fun format(block: List<String>): List<String> {
        val text = block.joinToString("\n")
        val attributes = Regex("<log4j:Event\\s+([^>]*)>").find(text)?.groupValues?.get(1).orEmpty()
        fun attr(name: String): String = Regex("$name=\"([^\"]*)\"").find(attributes)?.groupValues?.get(1).orEmpty()

        val logger = attr("logger").substringAfterLast('.')
        val level = attr("level")
        val thread = attr("thread")
        val timestamp = attr("timestamp").toLongOrNull()
        val message = extractCdata(text, "Message") ?: return block

        val header = "[${formatTime(timestamp)}] [$thread/$level] [$logger] $message"
        val throwable = extractCdata(text, "Throwable")
        if (throwable.isNullOrBlank()) return listOf(header)
        return listOf(header) + throwable.lines().filter { it.isNotBlank() }.map { "  $it" }
    }

    private fun extractCdata(text: String, tag: String): String? {
        val start = text.indexOf("<log4j:$tag")
        if (start < 0) return null
        val cdataStart = text.indexOf("CDATA[", start)
        val contentStart =
            if (cdataStart >= 0) cdataStart + "CDATA[".length
            else text.indexOf('>', start) + 1
        if (contentStart <= 0) return null
        val end = text.indexOf("]]", contentStart)
        return if (end >= 0) text.substring(contentStart, end) else text.substring(contentStart).trimEnd()
    }

    private fun formatTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return "--:--:--"
        return Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime().format(TIME_FORMAT)
    }

    private companion object {
        const val OPEN_TAG = "<log4j:Event"
        const val CLOSE_TAG = "</log4j:Event>"
        const val MAX_BLOCK_LINES = 200
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
