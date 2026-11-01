package dev.mmcl.plugin.runtime.adoptium

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.ArchiveFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class AdoptiumRuntimeProviderTest {
    private val provider = AdoptiumRuntimeProvider()

    @Test
    fun `macOS arm64 走 tar_gz`() = runBlocking {
        val download = provider.resolve(25, OperatingSystem.MACOS, Architecture.ARM64)

        assertEquals(
            "https://api.adoptium.net/v3/binary/latest/25/ga/mac/aarch64/jdk/hotspot/normal/eclipse",
            download?.url,
        )
        assertEquals(ArchiveFormat.TAR_GZ, download?.format)
        assertEquals("temurin-25.tar.gz", download?.fileName)
    }

    @Test
    fun `Windows x64 走 zip`() = runBlocking {
        val download = provider.resolve(21, OperatingSystem.WINDOWS, Architecture.X86_64)

        assertEquals(
            "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse",
            download?.url,
        )
        assertEquals(ArchiveFormat.ZIP, download?.format)
    }

    @Test
    fun `未知架构返回 null`() = runBlocking {
        assertNull(provider.resolve(21, OperatingSystem.LINUX, Architecture.UNKNOWN))
    }
}
