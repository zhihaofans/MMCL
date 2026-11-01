package dev.mmcl.core.platform

import dev.mmcl.api.OperatingSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectoryLauncherTest {
    private val dir: Path = Path.of("/tmp/mmcl plugins")

    @Test
    fun `macOS 用 open`() {
        assertEquals(listOf("open", dir.toString()), DirectoryLauncher(OperatingSystem.MACOS).command(dir))
    }

    @Test
    fun `Windows 用 explorer`() {
        assertEquals(
            listOf("explorer.exe", dir.toString()),
            DirectoryLauncher(OperatingSystem.WINDOWS).command(dir),
        )
    }

    @Test
    fun `Linux 用 xdg-open`() {
        assertEquals(
            listOf("xdg-open", dir.toString()),
            DirectoryLauncher(OperatingSystem.LINUX).command(dir),
        )
    }

    @Test
    fun `目录不存在时先建出来再打开`() {
        val base = Files.createTempDirectory("mmcl-dir-launcher")
        val target = base.resolve("plugins").resolve("nested")
        val started = mutableListOf<List<String>>()

        val result = DirectoryLauncher(OperatingSystem.LINUX) { started += it }.open(target)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
        assertTrue(Files.isDirectory(target))
        assertEquals(1, started.size)
        assertEquals("xdg-open", started.single().first())
        assertEquals(target.toAbsolutePath().normalize().toString(), started.single().last())

        base.toFile().deleteRecursively()
    }

    @Test
    fun `起进程失败时返回失败而不是抛异常`() {
        val result =
            DirectoryLauncher(OperatingSystem.LINUX) { error("no file manager") }
                .open(Files.createTempDirectory("mmcl-dir-launcher"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("no file manager"))
    }
}
