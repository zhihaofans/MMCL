package dev.mmcl.app

import java.util.Properties

/**
 * 构建期写入的启动器元信息。
 *
 * 版本号的唯一来源是 `gradle.properties`，构建时由 `processResources` 展开到
 * `/mmcl-build.properties`，这里只负责读取，不在代码里另写一份。
 */
internal object BuildInfo {
    val version: String by lazy {
        val properties = Properties()
        BuildInfo::class.java.getResourceAsStream("/mmcl-build.properties")?.use { stream ->
            properties.load(stream)
        }
        properties.getProperty("version", "0.0.0-dev")
    }
}
