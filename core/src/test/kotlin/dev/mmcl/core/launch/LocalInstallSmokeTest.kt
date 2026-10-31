package dev.mmcl.core.launch

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.download.FileDownloader
import dev.mmcl.core.download.GameInstaller
import dev.mmcl.core.event.DefaultEventBus
import dev.mmcl.core.java.JavaRuntimeDetector
import dev.mmcl.core.net.JdkHttpTransport
import dev.mmcl.core.platform.Platforms
import dev.mmcl.core.settings.InMemorySettingsService
import dev.mmcl.core.version.VersionDetailService
import dev.mmcl.core.version.VersionManifestEntry
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

/**
 * 本机已安装数据上的真实启动冒烟（不下载、不改数据）。
 *
 * 用途：排查“打包后的 app 启动游戏失败”这类问题，直接在数据目录上复现启动命令并打印游戏输出。
 *
 * ```
 * ./gradlew :core:test --tests "*LocalInstallSmokeTest*" -Pmmcl.localLaunch=1 --rerun-tasks
 * ```
 */
class LocalInstallSmokeTest {
    private val logger = LoggerFactory.getLogger("test")

    @Test
    fun `补全本机资源对象`() = runTest(timeout = 30.minutes) {
        if (System.getProperty("mmcl.downloadAssets") != "1") {
            println("跳过资源补全：加 -Pmmcl.downloadAssets=1 才会运行")
            return@runTest
        }
        val dataDir = Platforms.dataDir(OperatingSystem.MACOS)
        val transport = JdkHttpTransport()
        val detailService = VersionDetailService(dataDir.resolve("versions"), transport, logger)
        val versionId =
            Files.list(dataDir.resolve("versions")).use { stream ->
                stream.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.sorted().toList()
            }.lastOrNull() ?: error("数据目录里没有已安装版本")
        val entry = VersionManifestEntry(id = versionId, type = "release", url = "")
        val installer =
            GameInstaller(
                detailService = detailService,
                downloader = FileDownloader(transport, logger, concurrency = 8),
                root = dataDir,
                os = OperatingSystem.MACOS,
                arch = Architecture.ARM64,
                logger = logger,
                // 测试里直接映射 Mojang CDN；产品里由 source-mojang 插件提供
                assetUrl = { hash ->
                    if (hash.length < 2) null else "https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash"
                },
            )
        val result = installer.install(entry)
        println("文件：下载 ${result.report.downloaded}，跳过 ${result.report.skipped}，失败 ${result.report.failures.size}")
        println("资源：${result.assetReport}")
        result.report.failures.take(5).forEach { println("失败：${it.file.relativePath} ${it.error.message}") }
    }

    @Test
    fun `本机已安装的版本可以启动`() = runTest(timeout = 3.minutes) {
        if (System.getProperty("mmcl.localLaunch") != "1") {
            println("跳过本机启动冒烟：加 -Pmmcl.localLaunch=1 才会运行")
            return@runTest
        }
        val dataDir = Platforms.dataDir(OperatingSystem.MACOS)
        println("数据目录：$dataDir")
        val detailService = VersionDetailService(dataDir.resolve("versions"), JdkHttpTransport(), logger)
        val versionId =
            Files.list(dataDir.resolve("versions")).use { stream ->
                stream.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.sorted().toList()
            }.lastOrNull() ?: error("数据目录里没有已安装版本")
        println("使用版本：$versionId")
        val entry = VersionManifestEntry(id = versionId, type = "release", url = "")
        val detail = detailService.load(entry)
        val runtime =
            JavaRuntimeDetector(dataDir, OperatingSystem.MACOS, Architecture.ARM64, InMemorySettingsService(), logger)
                .detect(detail.javaVersion?.majorVersion ?: 0)
        println("运行时：${runtime?.executable}（${runtime?.source}，Java ${runtime?.majorVersion}）")

        val instanceDir = dataDir.resolve("instances/smoke")
        Files.createDirectories(instanceDir)
        val events = DefaultEventBus(logger)
        val seen = Collections.synchronizedList(mutableListOf<String>())
        events.subscribe(dev.mmcl.api.event.GameExited::class) { seen += "GameExited(${it.exitCode})" }
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val launcher =
            MinecraftLauncher(
                events,
                logger,
                logFile = { dataDir.resolve("logs/latest.log") },
            )
        val request =
            LaunchRequest(
                detail = detail,
                sharedRoot = dataDir,
                instanceDir = instanceDir,
                javaExecutable = runtime!!.executable,
                launcherVersion = "0.0.1",
                os = OperatingSystem.MACOS,
                arch = Architecture.ARM64,
                playerName = "SmokeTest",
                maxMemoryMb = 1024,
            )

        val session = launcher.launch(request) { line -> lines += line }
        println("进程 pid=${session.pid}")
        // 之前崩在约 6 秒的贴图加载阶段，这里至少观察 20 秒
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20)
        while (System.currentTimeMillis() < deadline && session.isRunning) {
            Thread.sleep(500)
        }
        val crashes =
            instanceDir.resolve("crash-reports").takeIf { Files.isDirectory(it) }
                ?.let { dir -> Files.list(dir).use { it.count() } } ?: 0
        println("启动后崩溃报告数：$crashes")
        if (!session.isRunning) println("进程已退出，code=${session.process.exitValue()}")
        println("事件：$seen")
        println("输出行数=${lines.size}")
        lines.takeLast(40).forEach { println("  | ${it.take(300)}") }
        if (!session.isRunning) println("进程已退出，code=${session.process.exitValue()}")
        session.stop()
        session.process.waitFor(10, TimeUnit.SECONDS)
    }
}
