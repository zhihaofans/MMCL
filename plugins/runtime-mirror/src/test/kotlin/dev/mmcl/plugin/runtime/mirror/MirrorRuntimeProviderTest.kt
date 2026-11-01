package dev.mmcl.plugin.runtime.mirror

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.ArchiveFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MirrorRuntimeProviderTest {
    private val requested = mutableListOf<String>()

    private fun listing(vararg files: String): String =
        files.joinToString("\n") { """<a href="$it">$it</a>""" }

    // ---------------- 清华 ----------------

    @Test
    fun `清华镜像按目录列表挑最高补丁版本`() = runTest {
        val provider =
            TunaTemurinProvider(fetchText = { url ->
                requested += url
                listing(
                    "OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.12.1_1.tar.gz",
                    "OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13_9.tar.gz",
                    "OpenJDK21U-debugimage_aarch64_linux_hotspot_21.0.13_9.tar.gz",
                )
            })

        val download = provider.resolve(21, OperatingSystem.LINUX, Architecture.ARM64)

        assertEquals(
            "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/aarch64/linux/",
            requested.single(),
        )
        assertEquals(
            "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/aarch64/linux/" +
                "OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13_9.tar.gz",
            download?.url,
        )
        assertEquals(ArchiveFormat.TAR_GZ, download?.format)
        assertEquals("OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13_9.tar.gz", download?.fileName)
    }

    @Test
    fun `Windows 拿 zip 而不是 tar_gz`() = runTest {
        val provider =
            TunaTemurinProvider(fetchText = {
                listing("OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip")
            })

        val download = provider.resolve(21, OperatingSystem.WINDOWS, Architecture.X86_64)

        assertEquals(ArchiveFormat.ZIP, download?.format)
        assertTrue(download?.url.orEmpty().endsWith(".zip"))
    }

    @Test
    fun `列表里没有对应平台的包时返回 null`() = runTest {
        val provider = TunaTemurinProvider(fetchText = { listing("OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz") })

        assertNull(provider.resolve(21, OperatingSystem.MACOS, Architecture.ARM64))
    }

    @Test
    fun `未知架构直接返回 null，不发请求`() = runTest {
        val download = TunaTemurinProvider(fetchText = { requested += it; "" })
            .resolve(21, OperatingSystem.LINUX, Architecture.UNKNOWN)

        assertNull(download)
        assertTrue(requested.isEmpty())
    }

    // ---------------- 中科大 ----------------

    @Test
    fun `中科大镜像走 LatestRelease 目录`() = runTest {
        val provider =
            UstcTemurinProvider(fetchText = { url ->
                requested += url
                listing("OpenJDK21U-jdk_x64_mac_hotspot_21.0.9_10.tar.gz")
            })

        val download = provider.resolve(21, OperatingSystem.MACOS, Architecture.X86_64)

        assertEquals(
            "https://mirrors.ustc.edu.cn/adoptium/releases/temurin21-binaries/LatestRelease/",
            requested.single(),
        )
        assertEquals("OpenJDK21U-jdk_x64_mac_hotspot_21.0.9_10.tar.gz", download?.fileName)
    }

    // ---------------- 华为云 ----------------

    @Test
    fun `华为云先选版本目录再选文件`() = runTest {
        val provider =
            HuaweiOpenJdkProvider(fetchText = { url ->
                requested += url
                when {
                    url.endsWith("/openjdk/") ->
                        listing("21.0.1/", "21.0.2/", "25.0.1/", "8.0.392/")
                    url.endsWith("/21.0.2/") ->
                        listing("openjdk-21.0.2_linux-x64_bin.tar.gz", "openjdk-21.0.2_windows-x64_bin.zip")
                    else -> ""
                }
            })

        val download = provider.resolve(21, OperatingSystem.WINDOWS, Architecture.X86_64)

        assertEquals(2, requested.size)
        assertEquals("https://repo.huaweicloud.com/openjdk/21.0.2/", requested[1])
        assertEquals(
            "https://repo.huaweicloud.com/openjdk/21.0.2/openjdk-21.0.2_windows-x64_bin.zip",
            download?.url,
        )
        assertEquals(ArchiveFormat.ZIP, download?.format)
    }

    @Test
    fun `华为云没有对应大版本时返回 null`() = runTest {
        val provider =
            HuaweiOpenJdkProvider(fetchText = { url ->
                if (url.endsWith("/openjdk/")) listing("17.0.9/") else ""
            })

        assertNull(provider.resolve(21, OperatingSystem.LINUX, Architecture.X86_64))
    }

    // ---------------- 工具函数 ----------------

    @Test
    fun `版本比较按数字段而不是字典序`() {
        // 按字符串比 "21.0.9" 会排在 "21.0.13" 后面，这里必须反过来
        assertTrue("21.0.13_9".asVersionKey() > "21.0.9_10".asVersionKey())
        assertTrue("21.0.2".asVersionKey() > "21.0.1".asVersionKey())
        assertTrue("25.0.1".asVersionKey() > "21.0.13_9".asVersionKey())
    }

    @Test
    fun `pickLatestVersion 只认指定大版本`() {
        val body = listing("21.0.1/", "25.0.1/", "21.0.3/")

        assertEquals("21.0.3", pickLatestVersion(body, 21, Regex("""href="(\d+(?:\.\d+)*)/"""")))
        assertNull(pickLatestVersion(body, 22, Regex("""href="(\d+(?:\.\d+)*)/"""")))
    }
}
