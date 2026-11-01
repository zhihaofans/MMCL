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
                val archive = work.resolve(download.fileName ?: "runtime-$majorVersion.$extension")
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
                extract(archive, download.format, extracted)
                val home =
                    findJdkHome(extracted)
                        ?: throw IOException("压缩包里找不到 JDK 目录（缺少 release 文件）")
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

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }
}
