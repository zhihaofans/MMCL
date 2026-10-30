package dev.mmcl.core.download

import dev.mmcl.api.OperatingSystem
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
 * MMCL_NET_TESTS=1 ./gradlew :core:test --tests "*RealMojangSmokeTest*" --rerun-tasks
 * ```
 */
class RealMojangSmokeTest {
    private val logger = LoggerFactory.getLogger("test")

    @Test
    fun `真实清单与详情可以解析并规划`() = runTest {
        if (System.getenv("MMCL_NET_TESTS") != "1") {
            println("跳过网络冒烟测试：设置 MMCL_NET_TESTS=1 才会运行")
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
