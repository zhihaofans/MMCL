package dev.mmcl.core.settings

import dev.mmcl.api.service.SettingsService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 基于 properties 文件的设置存储，写在 `<dataDir>/settings.properties`。
 *
 * 设计取舍：
 * - 只存字符串键值，复杂配置由插件自己序列化成 JSON 放到 `plugin-data/`；
 * - 写入走"临时文件 + 原子替换"，避免崩溃时写坏文件；
 * - 输出按键排序，Git 对比/人工排查时稳定。
 *
 * @param file 设置文件路径，父目录不存在会自动创建
 */
class FileSettingsService(private val file: Path) : SettingsService {
    private val lock = Any()
    private val values: MutableMap<String, String> = load()

    override fun getString(key: String, default: String?): String? =
        synchronized(lock) { values[key] ?: default }

    override fun putString(key: String, value: String?) {
        synchronized(lock) {
            if (value == null) values.remove(key) else values[key] = value
            persist()
        }
    }

    private fun load(): MutableMap<String, String> {
        if (!Files.isRegularFile(file)) return mutableMapOf()
        return Files.readAllLines(file, Charsets.UTF_8)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap(mutableMapOf())
    }

    private fun persist() {
        val parent = file.toAbsolutePath().parent
        if (parent != null) Files.createDirectories(parent)
        val body = values.toSortedMap().entries.joinToString(separator = "\n") { "${it.key}=${it.value}" }
        val temp = Files.createTempFile(parent, "settings", ".tmp")
        Files.writeString(temp, if (body.isEmpty()) "" else "$body\n", Charsets.UTF_8)
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
    }
}
