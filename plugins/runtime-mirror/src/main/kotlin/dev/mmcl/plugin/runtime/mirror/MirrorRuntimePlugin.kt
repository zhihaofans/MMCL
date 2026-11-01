package dev.mmcl.plugin.runtime.mirror

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.ArchiveFormat
import dev.mmcl.api.capability.JavaRuntimeProvider
import dev.mmcl.api.capability.RuntimeDownload
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 国内镜像运行时来源插件。
 *
 * foojay / Adoptium 官方接口在国内经常慢到不可用，但二进制本身有现成的国内镜像：
 * - 清华 TUNA：Adoptium（Temurin）
 * - 中科大 USTC：Adoptium（Temurin，`LatestRelease` 目录）
 * - 华为云：Oracle OpenJDK 的 GA 构建
 *
 * 镜像站只提供文件列表、没有"最新版"接口，所以这里读目录列表、按版本号取最高的一个。
 * 地址只存在于插件里，应用本身不含任何下载地址。
 */
class MirrorRuntimePlugin : Plugin {
    override val id: String = "dev.mmcl.runtime.mirror"
    override val version: String = "0.0.1"

    override fun onLoad(context: PluginContext) {
        context.services.register(JavaRuntimeProvider::class, TunaTemurinProvider())
        context.services.register(JavaRuntimeProvider::class, UstcTemurinProvider())
        context.services.register(JavaRuntimeProvider::class, HuaweiOpenJdkProvider())
        context.logger.info("国内镜像运行时来源已注册：清华 TUNA / 中科大 USTC / 华为云")
    }
}

/** Temurin（Adoptium 产品线）的清华镜像。 */
class TunaTemurinProvider(
    override val priority: Int = 20,
    private val fetchText: suspend (String) -> String = ::httpGetText,
) : JavaRuntimeProvider {
    override val id: String = "tuna:temurin"
    override val displayName: String = "Temurin（清华 TUNA 镜像）"

    override suspend fun resolve(
        majorVersion: Int,
        os: OperatingSystem,
        arch: Architecture,
    ): RuntimeDownload? {
        val osToken = adoptiumOsToken(os) ?: return null
        val archToken = adoptiumArchToken(arch) ?: return null
        val directory = "$BASE/$majorVersion/jdk/$archToken/$osToken/"
        val body = fetchText(directory)
        val file =
            pickLatestFile(
                body,
                Regex("""OpenJDK${majorVersion}U-jdk_${archToken}_${osToken}_hotspot_([0-9._]+)\.(?:tar\.gz|zip)"""),
            ) ?: return null
        return download(directory, file, os)
    }

    private companion object {
        /** 清华 Adoptium 镜像根（插件内部常量）。 */
        const val BASE: String = "https://mirrors.tuna.tsinghua.edu.cn/Adoptium"
    }
}

/** Temurin 的中科大镜像；用 `LatestRelease` 目录，避免自己遍历版本。 */
class UstcTemurinProvider(
    override val priority: Int = 25,
    private val fetchText: suspend (String) -> String = ::httpGetText,
) : JavaRuntimeProvider {
    override val id: String = "ustc:temurin"
    override val displayName: String = "Temurin（中科大 USTC 镜像）"

    override suspend fun resolve(
        majorVersion: Int,
        os: OperatingSystem,
        arch: Architecture,
    ): RuntimeDownload? {
        val osToken = adoptiumOsToken(os) ?: return null
        val archToken = adoptiumArchToken(arch) ?: return null
        val directory = "$BASE$majorVersion-binaries/LatestRelease/"
        val body = fetchText(directory)
        val file =
            pickLatestFile(
                body,
                Regex("""OpenJDK${majorVersion}U-jdk_${archToken}_${osToken}_hotspot_([0-9._]+)\.(?:tar\.gz|zip)"""),
            ) ?: return null
        return download(directory, file, os)
    }

    private companion object {
        /** 中科大 Adoptium releases 镜像根（插件内部常量）。 */
        const val BASE: String = "https://mirrors.ustc.edu.cn/adoptium/releases/temurin"
    }
}

/**
 * 华为云镜像的 Oracle OpenJDK（GA 构建）。
 *
 * 布局是两层的：`/openjdk/<版本>/<文件>`，所以要先把版本目录列出来挑一个。
 */
class HuaweiOpenJdkProvider(
    override val priority: Int = 30,
    private val fetchText: suspend (String) -> String = ::httpGetText,
) : JavaRuntimeProvider {
    override val id: String = "huawei:openjdk"
    override val displayName: String = "OpenJDK（华为云镜像）"

    override suspend fun resolve(
        majorVersion: Int,
        os: OperatingSystem,
        arch: Architecture,
    ): RuntimeDownload? {
        val osToken =
            when (os) {
                OperatingSystem.MACOS -> "macos"
                OperatingSystem.WINDOWS -> "windows"
                OperatingSystem.LINUX -> "linux"
            }
        val archToken = adoptiumArchToken(arch) ?: return null

        val version =
            pickLatestVersion(fetchText(BASE), majorVersion, Regex("""href="(\d+(?:\.\d+)*)/"""")) ?: return null
        val directory = "$BASE$version/"
        val body = fetchText(directory)
        val file =
            pickLatestFile(
                body,
                Regex("""openjdk-([0-9.]+)_${osToken}-${archToken}_bin\.(?:tar\.gz|zip)"""),
            ) ?: return null
        return download(directory, file, os)
    }

    private companion object {
        /** 华为云 OpenJDK 镜像根（插件内部常量）。 */
        const val BASE: String = "https://repo.huaweicloud.com/openjdk/"
    }
}

/** Adoptium 风格的 OS 目录名；macOS 在镜像里叫 `mac`。 */
internal fun adoptiumOsToken(os: OperatingSystem): String? =
    when (os) {
        OperatingSystem.MACOS -> "mac"
        OperatingSystem.WINDOWS -> "windows"
        OperatingSystem.LINUX -> "linux"
    }

/** Adoptium 风格的架构目录名。 */
internal fun adoptiumArchToken(arch: Architecture): String? =
    when (arch) {
        Architecture.ARM64 -> "aarch64"
        Architecture.X86_64 -> "x64"
        Architecture.UNKNOWN -> null
    }

/** Windows 拿 zip，其余平台拿 tar.gz。 */
internal fun download(directory: String, fileName: String, os: OperatingSystem): RuntimeDownload =
    RuntimeDownload(
        url = directory.trimEnd('/') + "/" + fileName,
        format = if (os == OperatingSystem.WINDOWS) ArchiveFormat.ZIP else ArchiveFormat.TAR_GZ,
        fileName = fileName,
    )

/**
 * 从目录列表里挑版本号最高的一个文件名。
 *
 * 正则的第 1 个分组必须是版本号，用来比较大小；返回整个匹配到的文件名。
 */
internal fun pickLatestFile(body: String, pattern: Regex): String? =
    pattern.findAll(body)
        .mapNotNull { match -> match.groupValues.getOrNull(1)?.let { version -> version to match.value } }
        .maxByOrNull { it.first.asVersionKey() }
        ?.second

/** 从目录列表里挑一个主版本匹配、版本号最高的目录名（会带结尾的 `/`）。 */
internal fun pickLatestVersion(body: String, majorVersion: Int, pattern: Regex): String? =
    pattern.findAll(body)
        .mapNotNull { it.groupValues.getOrNull(1)?.takeIf { value -> value.startsWith("$majorVersion.") || value == "$majorVersion" } }
        .maxByOrNull { it.asVersionKey() }

/**
 * 把 `21.0.13_9` 这种版本串归一成可直接按字符串比较的键。
 *
 * 每段补到 6 位再拼回去：这样 `21.0.13` 排在 `21.0.9` 之后，而不会按字典序排反。
 */
internal fun String.asVersionKey(): String =
    split('.', '_', '-', '+')
        .take(4)
        .joinToString(".") { (it.toIntOrNull() ?: 0).toString().padStart(6, '0') }

/** 取一个目录页的文本；失败抛 [IOException]，由调用方决定是否换下一个来源。 */
internal suspend fun httpGetText(url: String): String =
    withContext(Dispatchers.IO) {
        val client =
            HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        val request =
            HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()
        val response =
            try {
                client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            } catch (e: IOException) {
                throw IOException("请求镜像目录失败：$url", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("请求镜像目录被中断：$url", e)
            }
        if (response.statusCode() !in 200..299) {
            throw IOException("HTTP ${response.statusCode()}：$url")
        }
        response.body()
    }

private const val USER_AGENT = "MMCL"

private val TIMEOUT: Duration = Duration.ofSeconds(20)
