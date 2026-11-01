package dev.mmcl.core.java

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.ArchiveFormat
import dev.mmcl.api.capability.RuntimeDownload
import dev.mmcl.core.net.HttpTransport
import dev.mmcl.core.settings.InMemorySettingsService
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.slf4j.LoggerFactory

class JavaRuntimeInstallerTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var dataDir: Path
    private lateinit var installer: JavaRuntimeInstaller

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("mmcl-runtime-test")
        installer =
            JavaRuntimeInstaller(
                transport = BytesTransport(ByteArray(0)),
                logger = logger,
                runtimeRoot = dataDir.resolve("runtime"),
                os = OperatingSystem.MACOS,
                arch = Architecture.ARM64,
            )
    }

    @AfterTest
    fun tearDown() {
        dataDir.toFile().deleteRecursively()
    }

    private fun installerFor(bytes: ByteArray) =
        JavaRuntimeInstaller(
            transport = BytesTransport(bytes),
            logger = logger,
            runtimeRoot = dataDir.resolve("runtime"),
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
        )

    @Test
    fun `安装 zip 运行时后检测器能找到`() = runTest {
        val target =
            installerFor(zipFixture("jdk-25/release", "JAVA_VERSION=\"25.0.1\""))
                .install(25, RuntimeDownload(url = "https://example.invalid/jdk.zip"))

        assertTrue(Files.isRegularFile(target.resolve("release")))
        assertTrue(Files.isRegularFile(target.resolve("bin").resolve("java")))
        assertEquals(
            "runtime/macos-arm64/jdk-25",
            // Windows 上 Path.toString() 用反斜杠，断言前统一成正斜杠
            dataDir.relativize(target).toString().replace('\\', '/'),
        )

        val detector = detector()
        val runtime = detector.detect(25)
        assertEquals(JavaRuntimeSource.BUNDLED, runtime?.source)
        assertEquals(25, runtime?.majorVersion)
    }

    @Test
    fun `安装 tar_gz 运行时`() = runTest {
        val target =
            installerFor(tarGzFixture())
                .install(25, RuntimeDownload(url = "https://example.invalid/jdk.tar.gz", format = ArchiveFormat.TAR_GZ))

        assertTrue(Files.isRegularFile(target.resolve("release")))
        assertTrue(Files.isRegularFile(target.resolve("bin").resolve("java")))
    }

    @Test
    fun `SHA-1 不符时报错且不留下安装目录`() = runTest {
        val bytes = zipFixture("jdk-25/release", "JAVA_VERSION=\"25.0.1\"")

        assertFailsWith<IOException> {
            installerFor(bytes).install(
                25,
                RuntimeDownload(url = "https://example.invalid/jdk.zip", sha1 = "deadbeef"),
            )
        }
        assertTrue(!Files.exists(dataDir.resolve("runtime/macos-arm64/jdk-25")))
    }

    @Test
    fun `OS_ARCH 为空时视为缺失而不是误杀`() = runTest {
        val release = "JAVA_VERSION=\"25.0.1\"\nOS_NAME=\"Mac OS X\"\nOS_ARCH=\"\""
        val target = installerFor(zipFixture("jdk-25/release", release)).install(25, RuntimeDownload(url = "https://example.invalid/jdk.zip"))

        assertTrue(Files.isRegularFile(target.resolve("release")))
    }

    @Test
    fun `平台不符时报错`() = runTest {
        val release = "JAVA_VERSION=\"25.0.1\"\nOS_NAME=\"Linux\"\nOS_ARCH=\"aarch64\""

        assertFailsWith<IOException> {
            installerFor(zipFixture("jdk-25/release", release)).install(25, RuntimeDownload(url = "https://example.invalid/jdk.zip"))
        }
    }

    @Test
    fun `声明格式与实际不符时按魔数处理`() = runTest {
        val target =
            installerFor(tarGzFixture())
                .install(
                    25,
                    RuntimeDownload(
                        url = "https://example.invalid/jdk.zip",
                        format = ArchiveFormat.ZIP,
                        fileName = "jdk.zip",
                    ),
                )

        assertTrue(Files.isRegularFile(target.resolve("release")))
    }

    @Test
    fun `无法识别的压缩包报错`() = runTest {
        assertFailsWith<IOException> {
            installerFor("not-an-archive".toByteArray()).install(25, RuntimeDownload(url = "https://example.invalid/jdk.zip"))
        }
    }

    @Test
    fun `版本低于要求时报错`() = runTest {
        val bytes = zipFixture("jdk-17/release", "JAVA_VERSION=\"17.0.12\"")

        assertFailsWith<IOException> {
            installerFor(bytes).install(25, RuntimeDownload(url = "https://example.invalid/jdk.zip"))
        }
    }

    @Test
    fun `平台不符的压缩包会被拒绝`() = runTest {
        // 在 macOS arm64 上拿到 Linux x64 的包：版本号没问题，但装上去游戏会崩
        val bytes =
            zipFixture(
                "jdk-25/release",
                "JAVA_VERSION=\"25.0.1\"\nOS_NAME=\"Linux\"\nOS_ARCH=\"x86_64\"",
            )

        val error =
            assertFailsWith<IOException> {
                installerFor(bytes).install(25, RuntimeDownload(url = "https://example.invalid/jdk.zip"))
            }

        assertTrue(error.message.orEmpty().contains("Linux"), error.message.orEmpty())
        assertTrue(!Files.exists(dataDir.resolve("runtime/macos-arm64/jdk-25")))
    }

    @Test
    fun `架构不符的压缩包会被拒绝`() = runTest {
        // 同一个 macOS，但 arm64 机器拿到 x64 的包
        val bytes =
            zipFixture(
                "jdk-25/release",
                "JAVA_VERSION=\"25.0.1\"\nOS_NAME=\"Darwin\"\nOS_ARCH=\"x86_64\"",
            )

        val error =
            assertFailsWith<IOException> {
                installerFor(bytes).install(25, RuntimeDownload(url = "https://example.invalid/jdk.zip"))
            }

        assertTrue(error.message.orEmpty().contains("x86_64"), error.message.orEmpty())
    }

    @Test
    fun `平台匹配的压缩包正常安装`() = runTest {
        val bytes =
            zipFixture(
                "jdk-25/release",
                "JAVA_VERSION=\"25.0.1\"\nOS_NAME=\"Darwin\"\nOS_ARCH=\"aarch64\"",
            )

        val target = installerFor(bytes).install(25, RuntimeDownload(url = "https://example.invalid/jdk.zip"))

        assertTrue(Files.isRegularFile(target.resolve("release")))
    }

    private fun detector() =
        JavaRuntimeDetector(
            dataDir = dataDir,
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
            settings = InMemorySettingsService(),
            logger = logger,
            env = { null },
            pathLookup = { null },
            commandRunner = { null },
        )

    private fun zipFixture(releasePath: String, releaseContent: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            add(releasePath, releaseContent)
            add("jdk-25/bin/java", "#!/bin/sh\n")
        }
        return buffer.toByteArray()
    }

    private fun tarGzFixture(): ByteArray {
        val buffer = ByteArrayOutputStream()
        GzipCompressorOutputStream(buffer).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                fun add(name: String, content: String) {
                    val bytes = content.toByteArray()
                    val entry = TarArchiveEntry(name).apply { size = bytes.size.toLong() }
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
                add("jdk-25/release", "JAVA_VERSION=\"25.0.1\"")
                add("jdk-25/bin/java", "#!/bin/sh\n")
            }
        }
        return buffer.toByteArray()
    }

    private class BytesTransport(private val bytes: ByteArray) : HttpTransport {
        override suspend fun getText(url: String): String = ""

        override suspend fun downloadTo(url: String, target: Path) {
            Files.write(target, bytes)
        }
    }
}
