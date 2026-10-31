package dev.mmcl.core.download

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.event.DefaultEventBus
import dev.mmcl.core.java.JavaRuntimeDetector
import dev.mmcl.core.launch.LaunchRequest
import dev.mmcl.core.launch.MinecraftLauncher
import dev.mmcl.core.settings.InMemorySettingsService
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import dev.mmcl.core.net.JdkHttpTransport
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.core.version.VersionManifest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * 网络冒烟测试：用**真实** Mojang 数据验证清单/详情解析与文件规划。
 *
 * 默认跳过（CI 与离线环境不该依赖外网），本地手动执行：
 * ```
 * ./gradlew :core:test --tests "*RealMojangSmokeTest*" -Pmmcl.netTests=1 --rerun-tasks
 * # 连真实下载 + 启动一起跑（约 130MB，会弹出游戏窗口）
 * ./gradlew :core:test --tests "*RealMojangSmokeTest*" -Pmmcl.netTests=1 -Pmmcl.realLaunch=1 --rerun-tasks
 * ```
 */
class RealMojangSmokeTest {
    private val logger = LoggerFactory.getLogger("test")

    @Test
    fun `真实下载并启动最新正式版`() = runTest(timeout = 12.minutes) {
        if (System.getProperty("mmcl.realLaunch") != "1") {
            println("跳过真实启动测试：加 -Pmmcl.realLaunch=1 才会运行")
            return@runTest
        }
        val transport = JdkHttpTransport(userAgent = "MMCL-real-launch")
        val manifestJson = transport.getText("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        val manifest =
            Json { ignoreUnknownKeys = true }.decodeFromString(VersionManifest.serializer(), manifestJson)
        val latest = manifest.latestRelease() ?: error("清单里没有正式版")
        val dir = Files.createTempDirectory("mmcl-real-launch")
        println("工作目录：$dir")
        try {
            val detailService = VersionDetailService(dir.resolve("versions"), transport, logger)
            val installer =
                GameInstaller(
                    detailService = detailService,
                    downloader = FileDownloader(transport, logger, concurrency = 6),
                    root = dir,
                    os = OperatingSystem.MACOS,
                    arch = Architecture.ARM64,
                    logger = logger,
                )
            val result = installer.install(latest)
            println("下载结果：downloaded=${result.report.downloaded} skipped=${result.report.skipped} failures=${result.report.failures.size}")
            result.report.failures.take(3).forEach { println("失败：${it.file.relativePath} ${it.error.message}") }

            val runtime =
                JavaRuntimeDetector(dir, OperatingSystem.MACOS, Architecture.ARM64, InMemorySettingsService(), logger)
                    .detect(result.plan.detail.javaVersion?.majorVersion ?: 0)
            println("运行时：${runtime?.executable}（${runtime?.source}）")

            val lines = Collections.synchronizedList(mutableListOf<String>())
            val events = DefaultEventBus(logger)
            val eventsSeen = Collections.synchronizedList(mutableListOf<String>())
            events.subscribe(dev.mmcl.api.event.GameLaunching::class) { eventsSeen += "GameLaunching" }
            events.subscribe(dev.mmcl.api.event.GameExited::class) { eventsSeen += "GameExited(${it.exitCode})" }
            val launcher = MinecraftLauncher(events, logger)
            val request =
                LaunchRequest(
                    detail = result.plan.detail,
                    sharedRoot = dir,
                    instanceDir = dir.resolve("instances/smoke"),
                    javaExecutable = runtime!!.executable,
                    launcherVersion = "0.0.1",
                    os = OperatingSystem.MACOS,
                    arch = Architecture.ARM64,
                    auth = dev.mmcl.api.capability.Account(uuid = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:SmokeTest".toByteArray()).toString(), name = "SmokeTest"),
                    maxMemoryMb = 1024,
                )
            val session = launcher.launch(request) { line -> lines += line }
            println("进程 pid=${session.pid}")

            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(90)
            while (System.currentTimeMillis() < deadline && session.isRunning) {
                val joined = lines.joinToString("\n")
                if (joined.contains("LWJGL", ignoreCase = true) || joined.contains("Setting user", ignoreCase = true) ||
                    joined.contains("Backend library", ignoreCase = true) || joined.contains("OpenAL", ignoreCase = true)
                ) {
                    break
                }
                Thread.sleep(500)
            }
            println("事件：$eventsSeen")
            println("输出行数=${lines.size}，前 15 行：")
            lines.take(15).forEach { println("  | $it") }
            val markers = listOf("LWJGL", "Setting user", "Backend library", "OpenAL", "Exception", "Error")
            markers.forEach { marker ->
                val hit = lines.firstOrNull { it.contains(marker, ignoreCase = true) }
                if (hit != null) println("命中 [$marker]：${hit.take(160)}")
            }
            session.stop()
            session.process.waitFor(30, TimeUnit.SECONDS)
        } finally {
            println("（临时目录保留供排查：$dir）")
        }
    }

    @Test
    fun `真实清单与详情可以解析并规划`() = runTest {
        if (System.getProperty("mmcl.netTests") != "1") {
            println("跳过网络冒烟测试：加 -Pmmcl.netTests=1 才会运行")
            return@runTest
        }
        val transport = JdkHttpTransport(userAgent = "MMCL-smoke-test")
        val manifestJson = transport.getText("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        val manifest =
            Json { ignoreUnknownKeys = true }
                .decodeFromString(VersionManifest.serializer(), manifestJson)
        val latest = manifest.latestRelease() ?: error("清单里没有正式版")
        val dir = Files.createTempDirectory("mmcl-real-smoke")
        try {
            val installer =
                GameInstaller(
                    detailService = VersionDetailService(dir.resolve("versions"), transport, logger),
                    downloader = FileDownloader(transport, logger, concurrency = 2),
                    root = dir,
                    os = OperatingSystem.MACOS,
                    arch = Architecture.ARM64,
                    logger = logger,
                )

            val plan = installer.plan(latest)

            println(
                "真实规划 ${plan.versionId}：${plan.fileCount} 个文件，" +
                    "${plan.totalBytes} 字节，警告 ${plan.warnings.size} 条",
            )
            assertTrue(plan.fileCount > 50, "正式版应该包含大量库文件，实际 ${plan.fileCount}")
            assertTrue(
                plan.files.any { it.relativePath == "versions/${latest.id}/${latest.id}.jar" },
                "缺少客户端 JAR",
            )
            assertTrue(plan.files.any { it.relativePath == "assets/indexes/" + (plan.detail.assetIndex?.id ?: "") + ".json" }, "缺少资源索引")
            assertTrue(plan.detail.mainClass.isNotBlank(), "缺少 mainClass")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
