package dev.mmcl.core.version

import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.core.settings.InMemorySettingsService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class VersionManifestServiceTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var dir: Path
    private var now: Long = 1_700_000_000_000

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("mmcl-manifest-test")
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun cacheFile(): Path = dir.resolve("cache").resolve("version_manifest_v2.json")

    private fun service(
        vararg sources: VersionManifestSource,
        settings: InMemorySettingsService? = null,
        ttlMillis: Long = 60_000,
    ): VersionManifestService =
        VersionManifestService(
            cacheFile = cacheFile(),
            logger = logger,
            sources = { sources.toList() },
            settings = settings,
            clock = { now },
            cacheTtlMillis = ttlMillis,
        )

    private class FakeSource(
        override val id: String,
        override val priority: Int = 100,
        private val body: String = SAMPLE_MANIFEST,
        var failure: Throwable? = null,
    ) : VersionManifestSource {
        var calls: Int = 0
            private set

        override val displayName: String = "源 $id"

        override suspend fun fetchManifestJson(): String {
            calls++
            failure?.let { throw it }
            return body
        }
    }

    @Test
    fun `没有源插件且没有缓存时抛 NoVersionSourceException`() = runTest {
        assertFailsWith<NoVersionSourceException> { service().load() }
    }

    @Test
    fun `没有源插件但有缓存时使用缓存`() = runTest {
        service(FakeSource("mojang")).load()

        val manifest = service().load()

        assertEquals("1.21.8", manifest.latest.release)
    }

    @Test
    fun `按 priority 升序尝试，前一个失败自动换下一个`() = runTest {
        val broken = FakeSource("broken", priority = 10).apply { failure = IllegalStateException("挂了") }
        val working = FakeSource("working", priority = 20)

        val manifest = service(broken, working).load()

        assertEquals(1, broken.calls)
        assertEquals(1, working.calls)
        assertEquals("1.21.8", manifest.latest.release)
        assertEquals("working", service(broken, working).cachedSourceId())
    }

    @Test
    fun `用户指定的源会排在最前`() = runTest {
        val first = FakeSource("a", priority = 10)
        val second = FakeSource("b", priority = 20)
        val settings = InMemorySettingsService().apply {
            putString(VersionManifestService.SOURCE_KEY, "b")
        }

        service(first, second, settings = settings).load()

        assertEquals(0, first.calls)
        assertEquals(1, second.calls)
    }

    @Test
    fun `缓存有效期内不重复请求`() = runTest {
        val source = FakeSource("mojang")
        val service = service(source)

        service.load()
        now += 10_000
        service.load()

        assertEquals(1, source.calls)
    }

    @Test
    fun `强制刷新会忽略缓存`() = runTest {
        val source = FakeSource("mojang")
        val service = service(source)

        service.load()
        service.load(forceRefresh = true)

        assertEquals(2, source.calls)
    }

    @Test
    fun `换了源之后缓存不再命中`() = runTest {
        service(FakeSource("mojang")).load()
        assertEquals("mojang", service().cachedSourceId())

        val mirror = FakeSource("bmclapi")
        service(mirror).load()

        assertEquals(1, mirror.calls)
        assertEquals("bmclapi", service(mirror).cachedSourceId())
    }

    @Test
    fun `所有源失败时回退到过期缓存`() = runTest {
        val working = FakeSource("mojang")
        service(working, ttlMillis = 30_000).load()

        now += 60_000
        val broken = FakeSource("mojang").apply { failure = IllegalStateException("网络断了") }

        val manifest = service(broken).load()

        assertEquals("1.21.8", manifest.latest.release)
    }

    @Test
    fun `所有源失败且无缓存时抛异常`() = runTest {
        val broken = FakeSource("mojang").apply { failure = IllegalStateException("网络断了") }

        assertFailsWith<VersionManifestException> { service(broken).load() }
    }

    @Test
    fun `脏 JSON 只会跳过该源`() = runTest {
        val dirty = FakeSource("dirty", priority = 10, body = "{ not json")
        val good = FakeSource("good", priority = 20)

        val manifest = service(dirty, good).load()

        assertEquals("1.21.8", manifest.latest.release)
        assertEquals("good", service(dirty, good).cachedSourceId())
    }

    @Test
    fun `辅助方法返回缓存信息`() = runTest {
        val service = service(FakeSource("mojang"))

        assertNull(service.cachedAt())
        assertNull(service.cachedSourceId())

        service.load()

        assertNotNull(service.cachedAt())
        assertEquals(now, service.cachedAt())
        assertEquals("mojang", service.cachedSourceId())
        assertTrue(service.hasSource())
    }

    @Test
    fun `辅助查询方法可用`() = runTest {
        val manifest = service(FakeSource("mojang")).load()

        assertEquals("1.21.8", manifest.latestRelease()?.id)
        assertEquals("25w31a", manifest.latestSnapshot()?.id)
        assertEquals(2, manifest.releases().size)
        assertNull(manifest.find("1.0.0-out-of-range"))
        assertEquals("2026-07-15T09:00:00Z", manifest.latestRelease()?.releaseInstant()?.toString())
    }

    private companion object {
        // 结构对齐真实清单，额外字段用来验证 ignoreUnknownKeys
        const val SAMPLE_MANIFEST: String =
            """
            {
              "latest": { "release": "1.21.8", "snapshot": "25w31a" },
              "versions": [
                {
                  "id": "25w31a",
                  "type": "snapshot",
                  "url": "https://piston-meta.mojang.com/v1/packages/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/25w31a.json",
                  "time": "2026-07-29T10:12:00+00:00",
                  "releaseTime": "2026-07-29T10:12:00+00:00",
                  "sha1": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "complianceLevel": 1,
                  "unknownField": "ignored"
                },
                {
                  "id": "1.21.8",
                  "type": "release",
                  "url": "https://piston-meta.mojang.com/v1/packages/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/1.21.8.json",
                  "time": "2026-07-15T09:00:00+00:00",
                  "releaseTime": "2026-07-15T09:00:00+00:00",
                  "sha1": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "complianceLevel": 1
                },
                {
                  "id": "1.20.1",
                  "type": "release",
                  "url": "https://piston-meta.mojang.com/v1/packages/cccccccccccccccccccccccccccccccccccccccc/1.20.1.json",
                  "time": "2023-06-12T16:20:00+00:00",
                  "releaseTime": "2023-06-12T16:20:00+00:00",
                  "sha1": "cccccccccccccccccccccccccccccccccccccccc",
                  "complianceLevel": 1
                }
              ]
            }
            """
    }
}
