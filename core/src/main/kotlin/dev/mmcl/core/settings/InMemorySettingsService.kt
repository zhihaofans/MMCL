package dev.mmcl.core.settings

import dev.mmcl.api.service.SettingsService
import java.util.concurrent.ConcurrentHashMap

/**
 * 内存版设置存储：用于测试，以及在没有数据目录的场景下构造 [dev.mmcl.core.plugin.PluginManager]。
 */
class InMemorySettingsService : SettingsService {
    private val values = ConcurrentHashMap<String, String>()

    override fun getString(key: String, default: String?): String? = values[key] ?: default

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
