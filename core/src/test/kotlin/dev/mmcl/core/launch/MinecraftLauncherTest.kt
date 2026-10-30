package dev.mmcl.core.launch

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.event.GameExited
import dev.mmcl.api.event.GameLaunching
import dev.mmcl.core.event.DefaultEventBus
import dev.mmcl.core.version.VersionDetail
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class MinecraftLauncherTest {
    private val logger = LoggerFactory.getLogger("test")
    private val events = DefaultEventBus(logger)

    private val detail = VersionDetail(id = "test-version", type = "release", mainClass = "FakeMain")

    private fun request() =
        LaunchRequest(
            detail = detail,
            gameDir = Path.of("/tmp/mmcl-launcher-test"),
            javaExecutable = Path.of("/bin/sh"),
            launcherVersion = "0.0.1",
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
        )

    @Test
    fun `启动后依次发 GameLaunching 与 GameExited`() {
        val launching = CopyOnWriteArrayList<GameLaunching>()
        val exited = CopyOnWriteArrayList<GameExited>()
        events.subscribe(GameLaunching::class) { launching += it }
        val latch = CountDownLatch(1)
        events.subscribe(GameExited::class) {
            exited += it
            latch.countDown()
        }
        val lines = CopyOnWriteArrayList<String>()
        // 用一个真实的假进程代替 java/Minecraft：打印一行后以退出码 3 结束
        val launcher =
            MinecraftLauncher(events, logger) { _, _ ->
                ProcessBuilder("/bin/sh", "-c", "echo hello-mmcl; exit 3").redirectErrorStream(true).start()
            }

        val session = launcher.launch(request()) { line -> lines += line }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "等待 GameExited 超时")
        assertEquals(1, launching.size)
        assertEquals(1, exited.size)
        assertEquals(3, exited.single().exitCode)
        assertTrue(lines.contains("hello-mmcl"))
        // 进程此时已退出（GameExited 是退出后发的）
        session.process.waitFor()
        assertEquals(false, session.isRunning)
    }

    @Test
    fun `进程拉不起来时抛 LaunchException`() {
        val launcher = MinecraftLauncher(events, logger) { _, _ -> throw IllegalStateException("boom") }

        assertFailsWith<LaunchException> { launcher.launch(request()) }
    }
}
