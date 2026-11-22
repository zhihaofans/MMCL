package dev.mmcl.core.launch

import dev.mmcl.core.download.GameFilePlanner
import dev.mmcl.core.version.VersionDetail
import dev.mmcl.core.version.resolvedArtifact
import java.io.File
import java.nio.file.Path

/**
 * 把版本详情 + 运行时信息拼成一条可直接交给 `ProcessBuilder` 的命令。
 *
 * 规则：
 * - 参数模板来自详情里的 `arguments`（jvm / game），按平台与 rules 求值后替换占位符；
 * - classpath = 允许的库（与下载计划同一份过滤结果）+ 客户端 JAR，分隔符用平台默认；
 * - natives 不手动解压：现代版本的 JVM 参数给了 `org.lwjgl.system.SharedLibraryExtractPath`，
 *   由 LWJGL 自己从 classpath 的 natives JAR 里解压；
 * - 日志配置（log4j）用详情里的 `logging.client.argument` 附加，`${path}` 指向已下载的配置文件；
 * - 账户是离线占位（名字 + 离线 UUID + token `0`），等 `auth-*` 插件替换。
 */
object LaunchCommandBuilder {
    /**
     * @throws LaunchException 版本缺少 `mainClass` 等关键信息时
     */
    fun build(request: LaunchRequest): LaunchCommand {
        val detail = request.detail
        if (detail.mainClass.isBlank()) {
            throw LaunchException("版本 ${detail.id} 缺少 mainClass，无法启动")
        }

        val librariesDir = request.sharedRoot.resolve("libraries")
        val assetsDir = request.sharedRoot.resolve("assets")
        // 加载器版本自己没有 client jar，文件在 root 版本目录下
        val baseVersionId = detail.baseVersionId
        val nativesDir = request.sharedRoot.resolve("versions").resolve(baseVersionId).resolve("natives")
        val clientJar = request.sharedRoot.resolve("versions").resolve(baseVersionId).resolve("$baseVersionId.jar")

        val classpath =
            buildList {
                GameFilePlanner.allowedLibraries(detail, request.os, request.arch)
                    .mapNotNull { it.resolvedArtifact() }
                    .forEach { artifact ->
                        val relative = artifact.path?.takeIf { it.isNotBlank() }
                        if (relative != null) add(librariesDir.resolve(relative).toString())
                    }
                add(clientJar.toString())
            }.distinct().joinToString(File.pathSeparator)

        val values =
            mapOf(
                "auth_player_name" to request.auth.name,
                "auth_uuid" to request.auth.uuid,
                "auth_access_token" to request.auth.accessToken,
                "auth_xuid" to "",
                "clientid" to "",
                "version_name" to detail.id,
                "version_type" to detail.type,
                "game_directory" to request.instanceDir.toString(),
                "assets_root" to assetsDir.toString(),
                "assets_index_name" to (detail.assetIndex?.id ?: detail.assets),
                "user_type" to request.auth.userType,
                "natives_directory" to nativesDir.toString(),
                "launcher_name" to request.launcherName,
                "launcher_version" to request.launcherVersion,
                "classpath" to classpath,
                "classpath_separator" to File.pathSeparator,
                "library_directory" to librariesDir.toString(),
                "resolution_width" to "",
                "resolution_height" to "",
            )

        val jvm =
            buildList {
                add("-Xmx${request.maxMemoryMb}M")
                addAll(Arguments.resolve(detail.arguments.jvm, request.os).map { substitute(it, values) })
                loggingArgument(detail, assetsDir)?.let { add(it) }
                addAll(request.extraJvmArgs)
            }

        val game = Arguments.resolve(detail.arguments.game, request.os).map { substitute(it, values) }

        return LaunchCommand(
            executable = request.javaExecutable,
            arguments = jvm + listOf(detail.mainClass) + game,
            classpath = classpath,
        )
    }

    private fun substitute(argument: String, values: Map<String, String>): String =
        values.entries.fold(argument) { acc, (key, value) -> acc.replace("\${$key}", value) }

    private fun loggingArgument(detail: VersionDetail, assetsDir: Path): String? {
        val logging = detail.logging?.client ?: return null
        val file = logging.file ?: return null
        if (logging.argument.isBlank()) return null
        val name = file.id.takeIf { it.isNotBlank() } ?: file.path?.substringAfterLast('/') ?: return null
        val path = assetsDir.resolve("log_configs").resolve(name)
        return logging.argument.replace("\${path}", path.toString())
    }
}
