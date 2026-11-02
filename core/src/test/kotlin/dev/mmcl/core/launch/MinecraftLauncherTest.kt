package dev.mmcl.core.launch

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.event.GameExited
import dev.mmcl.api.event.GameLaunching
import dev.mmcl.core.event.DefaultEventBus
import dev.mmcl.core.version.VersionDetail
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

class MinecraftLauncherTest {
    private val logger = LoggerFactory.getLogger("test")
    private val events = DefaultEventBus(logger)

    private val detail = VersionDetail(id = "test-version", type = "release", mainClass = "FakeMain")

    /** 当前 JDK 的 java 可执行文件；Windows 下是 java.exe。 */
    private fun javaExecutable(): String {
        val exe = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
        return Path.of(System.getProperty("java.home"), "bin", exe).toString()
    }

    /**
     * 造一个“假游戏进程”：把 [body] 作为 main 方法体写成临时 Java 源码，
     * 用单文件源码模式（JEP 330）交给当前 JDK 跑。
     *
     * 不用 `/bin/sh -c` 是因为 Windows 上没有 /bin/sh，`cmd /c` 的转义规则又完全不同；
     * 用同一个 JVM 既跨平台，又能精确控制输出时序与退出码。
     */
    private fun fakeGameProcess(body: String): Process {
        val dir = Files.createTempDirectory("mmcl-fake-game")
        val source = dir.resolve("FakeGame.java")
        val code =
            """
            public class FakeGame {
                public static void main(String[] args) throws Exception {
            $body
                }
            }
            """.trimIndent()
        Files.writeString(source, code)
        dir.toFile().deleteOnExit()
        source.toFile().deleteOnExit()
        return ProcessBuilder(javaExecutable(), source.toString())
            .redirectErrorStream(true)
            .start()
    }

    private fun request() =
        LaunchRequest(
            detail = detail,
            sharedRoot = Path.of("mmcl-launcher-test"),
            instanceDir = Path.of("mmcl-launcher-test/instances/default"),
            javaExecutable = Path.of(javaExecutable()),
            launcherVersion = "0.0.1",
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
            auth = dev.mmcl.api.capability.Account(uuid = "00000000-0000-0000-0000-000000000000", name = "Player"),
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
            MinecraftLauncher(events, logger) { _, _, _ ->
                fakeGameProcess(
                    """
                            System.out.println("hello-mmcl");
                            System.out.flush();
                            System.exit(3);
                    """.trimIndent(),
                )
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
    fun `输出会同时进入流 环形缓冲与日志文件`() {
        val logDir = Files.createTempDirectory("mmcl-game-log-test")
        val logFile = logDir.resolve("latest.log")
        val exited = CountDownLatch(1)
        events.subscribe(GameExited::class) { exited.countDown() }
        val launcher =
            MinecraftLauncher(events, logger, logFile = { logFile }) { _, _, _ ->
                fakeGameProcess(
                    """
                            Thread.sleep(600);
                            System.out.println("log-line-1");
                            System.out.flush();
                            Thread.sleep(150);
                            System.out.println("log-line-2");
                            System.out.flush();
                    """.trimIndent(),
                )
            }

        val session = launcher.launch(request())
        val received = CopyOnWriteArrayList<String>()
        runBlocking {
            withTimeout(10_000) {
                session.output.take(2).collect { received += it }
            }
        }

        assertTrue(exited.await(10, TimeUnit.SECONDS), "等待 GameExited 超时")
        assertEquals(listOf("log-line-1", "log-line-2"), received)
        assertTrue(session.snapshot().containsAll(listOf("log-line-1", "log-line-2")))
        val text = Files.readString(logFile)
        assertTrue(text.contains("log-line-1"), "日志文件缺少第一行：$text")
        assertTrue(text.contains("log-line-2"), "日志文件缺少第二行：$text")
        logDir.toFile().deleteRecursively()
    }

    @Test
    fun `进程拉不起来时抛 LaunchException`() {
        val launcher = MinecraftLauncher(events, logger) { _, _, _ -> throw IllegalStateException("boom") }

        assertFailsWith<LaunchException> { launcher.launch(request()) }
    }
}
