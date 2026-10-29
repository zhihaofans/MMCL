package dev.mmcl.core.platform

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformsTest {
    private val home = Path.of("/fake-home")

    @Test
    fun `macOS 使用 Application Support`() {
        assertEquals(
            Path.of("/fake-home/Library/Application Support/MMCL"),
            Platforms.dataDir(OperatingSystem.MACOS, home = home),
        )
    }

    @Test
    fun `Windows 使用 APPDATA`() {
        assertEquals(
            Path.of("/fake-appdata/MMCL"),
            Platforms.dataDir(OperatingSystem.WINDOWS, home = home, env = { if (it == "APPDATA") "/fake-appdata" else null }),
        )
    }

    @Test
    fun `Windows 缺少 APPDATA 时回落 Roaming`() {
        assertEquals(
            Path.of("/fake-home/AppData/Roaming/MMCL"),
            Platforms.dataDir(OperatingSystem.WINDOWS, home = home, env = { null }),
        )
    }

    @Test
    fun `Linux 优先使用 XDG_DATA_HOME`() {
        assertEquals(
            Path.of("/fake-xdg/mmcl"),
            Platforms.dataDir(OperatingSystem.LINUX, home = home, env = { if (it == "XDG_DATA_HOME") "/fake-xdg" else null }),
        )
    }

    @Test
    fun `Linux 缺少 XDG_DATA_HOME 时回落 local share`() {
        assertEquals(
            Path.of("/fake-home/.local/share/mmcl"),
            Platforms.dataDir(OperatingSystem.LINUX, home = home, env = { null }),
        )
    }

    @Test
    fun `操作系统探测`() {
        assertEquals(OperatingSystem.MACOS, Platforms.currentOs("Mac OS X"))
        assertEquals(OperatingSystem.WINDOWS, Platforms.currentOs("Windows 11"))
        assertEquals(OperatingSystem.LINUX, Platforms.currentOs("Linux"))
    }

    @Test
    fun `架构探测`() {
        assertEquals(Architecture.ARM64, Platforms.currentArch("aarch64"))
        assertEquals(Architecture.X86_64, Platforms.currentArch("amd64"))
        assertEquals(Architecture.UNKNOWN, Platforms.currentArch("riscv64"))
    }
}
