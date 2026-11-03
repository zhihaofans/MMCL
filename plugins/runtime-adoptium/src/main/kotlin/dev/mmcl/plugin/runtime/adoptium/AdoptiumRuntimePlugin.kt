package dev.mmcl.plugin.runtime.adoptium

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.ArchiveFormat
import dev.mmcl.api.capability.JavaRuntimeProvider
import dev.mmcl.api.capability.RuntimeDownload
import dev.mmcl.api.ApiLevel
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext

/**
 * Adoptium（Temurin）运行时来源插件。
 *
 * 地址只存在于插件里：应用本身不含任何下载地址。
 * 通过 Adoptium 的 `binary/latest` 接口取 GA 版本（会自动重定向到归档文件）。
 */
class AdoptiumRuntimePlugin : Plugin {
    override val id: String = "dev.mmcl.runtime.adoptium"
    override val version: String = "0.0.1"
    override val apiLevel: Int = ApiLevel.CURRENT

    override fun onLoad(context: PluginContext) {
        context.services.register(JavaRuntimeProvider::class, AdoptiumRuntimeProvider())
        context.logger.info("Adoptium 运行时来源已注册")
    }
}

/** Adoptium 的下载地址映射。 */
class AdoptiumRuntimeProvider : JavaRuntimeProvider {
    override val id: String = "adoptium"
    override val displayName: String = "Adoptium Temurin"

    override suspend fun resolve(
        majorVersion: Int,
        os: OperatingSystem,
        arch: Architecture,
    ): RuntimeDownload? {
        val osToken =
            when (os) {
                OperatingSystem.MACOS -> "mac"
                OperatingSystem.WINDOWS -> "windows"
                OperatingSystem.LINUX -> "linux"
            }
        val archToken =
            when (arch) {
                Architecture.ARM64 -> "aarch64"
                Architecture.X86_64 -> "x64"
                Architecture.UNKNOWN -> return null
            }
        val format = if (os == OperatingSystem.WINDOWS) ArchiveFormat.ZIP else ArchiveFormat.TAR_GZ
        val fileName = if (format == ArchiveFormat.ZIP) "temurin-$majorVersion.zip" else "temurin-$majorVersion.tar.gz"
        return RuntimeDownload(
            url = "$BASE/$majorVersion/ga/$osToken/$archToken/jdk/hotspot/normal/eclipse",
            format = format,
            fileName = fileName,
        )
    }

    companion object {
        /** Adoptium API 基址（插件内部常量）。 */
        const val BASE: String = "https://api.adoptium.net/v3/binary/latest"
    }
}
