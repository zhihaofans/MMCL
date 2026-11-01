package dev.mmcl.core.java

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.ArchiveFormat
import dev.mmcl.api.capability.RuntimeDownload
import dev.mmcl.core.download.Sha1
import dev.mmcl.core.net.HttpTransport
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.slf4j.Logger

/**
 * 下载并安装 Java 运行时（地址由 `JavaRuntimeProvider` 插件提供）。
 *
 * 流程：下载到临时文件 → 校验 SHA-1（有就校验）→ 解压 → 校验压缩包里的 JDK 版本 →
 * 移动到 `<dataDir>/runtime/<os>-<arch>/jdk-<major>/` → 删除临时目录。
 *
 * 安装后的目录结构正好是 [JavaRuntimeDetector.bundledHomes] 会扫描的布局，
 * 检测器无需改动。
 *
 * @param runtimeRoot 运行时根目录（`<dataDir>/runtime`）
 * @param onProgress 累计下载字节数回调
 */
class JavaRuntimeInstaller(
    private val transport: HttpTransport,
    private val logger: Logger,
    private val runtimeRoot: Path,
    private val os: OperatingSystem,
    private val arch: Architecture,
) {
    suspend fun install(
        majorVersion: Int,
        download: RuntimeDownload,
        onProgress: suspend (Long) -> Unit = {},
    ): Path =
        withContext(Dispatchers.IO) {
            val platformDir = runtimeRoot.resolve("${os.name.lowercase()}-${arch.name.lowercase()}")
            val target = platformDir.resolve("jdk-$majorVersion")
            Files.createDirectories(runtimeRoot)
            val work = Files.createTempDirectory(runtimeRoot, "java-download-")
            try {
                val extension = if (download.format == ArchiveFormat.ZIP) "zip" else "tar.gz"
                // 文件名来自插件：只保留 basename，再走路径校验，避免 ../../ 写到临时目录外
                val safeName =
                    download.fileName
                        ?.substringAfterLast('/')
                        ?.substringAfterLast('\\')
                        ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
                val archive = safeResolve(work, safeName ?: "runtime-$majorVersion.$extension")
                var received = 0L
                transport.downloadTo(download.url, archive) { bytes ->
                    received += bytes
                    onProgress(received)
                }
                val expectedSha1 = download.sha1
                if (!expectedSha1.isNullOrBlank() && !Sha1.matches(archive, expectedSha1)) {
                    throw IOException("运行时压缩包 SHA-1 校验失败")
                }

                val extracted = work.resolve("extracted")
                Files.createDirectories(extracted)
                val actualFormat = sniffFormat(archive)
                    ?: throw IOException("无法识别运行时压缩包格式（既不是 zip 也不是 tar.gz）")
                if (actualFormat != download.format) {
                    logger.warn(
                        "来源声明的压缩格式是 {}，实际是 {}；按实际格式处理",
                        download.format,
                        actualFormat,
                    )
                }
                extract(archive, actualFormat, extracted)
                val home =
                    findJdkHome(extracted)
                        ?: throw IOException("压缩包里找不到 JDK 目录（缺少 release 文件）")
                validatePlatform(home)
                val installedMajor =
                    readMajorVersion(home)
                        ?: throw IOException("无法从 release 文件解析 Java 版本")
                if (installedMajor < majorVersion) {
                    throw IOException("下载到的运行时是 Java $installedMajor，低于需要的 $majorVersion")
                }

                deleteRecursively(target)
                Files.createDirectories(target.parent)
                Files.move(home, target, StandardCopyOption.REPLACE_EXISTING)
                logger.info("Java {} 已安装到 {}（来源版本 {}）", majorVersion, target, installedMajor)
                target
            } finally {
                deleteRecursively(work)
            }
        }

    /** 按魔数判断格式：`PK` 是 zip，`1f 8b` 是 gzip。 */
    private fun sniffFormat(archive: Path): ArchiveFormat? {
        Files.newInputStream(archive).use { input ->
            val head = ByteArray(4)
            val read = input.read(head)
            if (read >= 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()) return ArchiveFormat.TAR_GZ
            if (read >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return ArchiveFormat.ZIP
        }
        return null
    }

    private fun extract(archive: Path, format: ArchiveFormat, destination: Path) {
        when (format) {
            ArchiveFormat.ZIP -> extractZip(archive, destination)
            ArchiveFormat.TAR_GZ -> extractTarGz(archive, destination)
        }
    }

    private fun extractZip(archive: Path, destination: Path) {
        ZipInputStream(BufferedInputStream(Files.newInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = safeResolve(destination, entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    target.parent?.let { Files.createDirectories(it) }
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
            }
        }
    }

    private fun extractTarGz(archive: Path, destination: Path) {
        GzipCompressorInputStream(BufferedInputStream(Files.newInputStream(archive))).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val target = safeResolve(destination, entry.name)
                    when {
                        entry.isDirectory -> Files.createDirectories(target)
                        entry.isSymbolicLink -> {
                            target.parent?.let { Files.createDirectories(it) }
                            runCatching {
                                Files.deleteIfExists(target)
                                Files.createSymbolicLink(target, Path.of(entry.linkName))
                            }.onFailure { logger.warn("创建符号链接失败（{} → {}）：{}", target, entry.linkName, it.message) }
                        }
                        else -> {
                            target.parent?.let { Files.createDirectories(it) }
                            Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }
            }
        }
    }

    private fun safeResolve(root: Path, name: String): Path {
        val relative = Path.of(name).normalize()
        val target = root.resolve(relative).normalize()
        if (relative.isAbsolute || !target.startsWith(root)) {
            throw IOException("压缩包包含非法路径：$name")
        }
        return target
    }

    private fun findJdkHome(root: Path): Path? =
        Files.walk(root, 4).use { stream ->
            stream
                .filter { Files.isDirectory(it) && Files.isRegularFile(it.resolve("release")) }
                .min(Comparator.comparingInt { it.nameCount })
                .orElse(null)
        }

    /**
     * 校验压缩包确实是**当前平台**的运行时。
     *
     * JDK 的 `release` 文件里带 `OS_NAME` / `OS_ARCH`（如 `OS_NAME="Darwin"`、`OS_ARCH="aarch64"`）。
     * 来源插件如果给错了包（最常见的是 macOS arm64 拿到 x64 的包），装完不会被版本校验发现：
     * 检测器照样认这个 JDK，直到启动游戏才因为 CPU 架构不符而崩。这里提前拦下。
     *
     * 字段缺失时跳过校验：个别发行版不带这两项，不能因此拒绝安装。
     */
    private fun validatePlatform(home: Path) {
        val release = readReleaseProperties(home) ?: return

        release["OS_NAME"]?.takeIf { it.isNotBlank() }?.let { declared ->
            val name = declared.trim('"').lowercase()
            val matches =
                when (os) {
                    OperatingSystem.WINDOWS -> name == "windows"
                    OperatingSystem.MACOS -> name == "darwin" || name == "macos" || name == "mac os x"
                    OperatingSystem.LINUX -> name == "linux"
                }
            if (!matches) {
                throw IOException(
                    "下载到的运行时是 $declared 版本，当前平台是 ${os.name.lowercase()}：来源可能给错了平台的包",
                )
            }
        }

        release["OS_ARCH"]?.takeIf { it.isNotBlank() }?.let { declared ->
            if (arch != Architecture.UNKNOWN &&
                normalizeArch(declared.trim('"')) != normalizeArch(arch.name.lowercase())
            ) {
                throw IOException(
                    "下载到的运行时是 $declared 架构，当前架构是 ${arch.name.lowercase()}：来源可能给错了平台的包",
                )
            }
        }
    }

    /** `release` 文件里的 `KEY="value"` 行解析成 map；读不到返回 `null`。 */
    private fun readReleaseProperties(home: Path): Map<String, String>? {
        val release = home.resolve("release")
        if (!Files.isRegularFile(release)) return null
        return runCatching { Files.readAllLines(release, Charsets.UTF_8) }
            .getOrNull()
            ?.mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = line.substring(0, index).trim().uppercase()
                val value = line.substring(index + 1).trim().trim('"')
                key to value
            }
            ?.toMap()
    }

    private fun normalizeArch(value: String): String =
        when (value.trim().trim('"').lowercase()) {
            "x86_64", "amd64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> value.trim().trim('"').lowercase()
        }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }
}
