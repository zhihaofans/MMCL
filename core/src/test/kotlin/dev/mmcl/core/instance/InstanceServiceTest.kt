package dev.mmcl.core.instance

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class InstanceServiceTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var root: Path
    private lateinit var service: InstanceService
    private var now: Long = 1_700_000_000_000

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("mmcl-instance-test")
        service =
            InstanceService(
                store = InstanceStore(root.resolve("instances.json"), logger),
                instancesDir = root.resolve("instances"),
                logger = logger,
                clock = { now++ },
            )
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `保存实例级启动参数并持久化`() {
        val instance = service.create("26.3")

        val ok = service.updateLaunchSettings(instance.id, 4096, "-XX:+UseG1GC -Dfoo=bar", "JAVA_TOOL_OPTIONS=-Dx=1")

        assertTrue(ok)
        val saved = service.list().single { it.id == instance.id }
        assertEquals(4096, saved.memoryMb)
        assertEquals("-XX:+UseG1GC -Dfoo=bar", saved.extraJvmArgs)
        assertEquals("JAVA_TOOL_OPTIONS=-Dx=1", saved.envVars)
    }

    @Test
    fun `对不存在的实例保存启动参数返回 false`() {
        assertFalse(service.updateLaunchSettings("nope", 1024, "", ""))
    }

    @Test
    fun `旧版实例 JSON 没有启动参数字段时按默认值读出`() {
        Files.writeString(
            root.resolve("instances.json"),
            """{"instances":[{"id":"old","name":"old","versionId":"26.3","createdAtEpochMillis":1,"lastLaunchedAtEpochMillis":0}]}""",
        )

        val instance = service.list().single()

        assertEquals(null, instance.memoryMb)
        assertEquals("", instance.extraJvmArgs)
        assertEquals("", instance.envVars)
    }

    @Test
    fun `创建实例会建目录与子目录并持久化`() {
        val instance = service.create("26.3")

        assertEquals("26.3", instance.name)
        assertEquals("26.3", instance.versionId)
        assertTrue(Files.isDirectory(service.directoryOf(instance)))
        listOf("saves", "mods", "config").forEach {
            assertTrue(Files.isDirectory(service.directoryOf(instance).resolve(it)), "缺少子目录 $it")
        }
        assertTrue(Files.isRegularFile(root.resolve("instances.json")))
        assertEquals(1, InstanceStore(root.resolve("instances.json"), logger).load().size)
    }

    @Test
    fun `同名实例自动加后缀`() {
        val first = service.create("26.3")
        val second = service.create("26.3")

        assertEquals("26.3", first.name)
        assertEquals("26.3 2", second.name)
        assertEquals("26.3", first.id)
        assertEquals("26.3-2", second.id)
    }

    @Test
    fun `删除实例会删目录并从清单移除`() {
        val instance = service.create("26.3")
        val directory = service.directoryOf(instance)

        assertTrue(service.delete(instance.id))

        assertFalse(Files.exists(directory))
        assertTrue(service.list().isEmpty())
        assertFalse(service.delete("missing"))
    }

    @Test
    fun `清单损坏时按空列表处理`() {
        Files.writeString(root.resolve("instances.json"), "{ broken")

        assertTrue(service.list().isEmpty())
        // 仍然可以继续创建
        assertEquals("26.3", service.create("26.3").name)
        assertEquals(1, service.list().size)
    }

    @Test
    fun `markLaunched 记录时间并按最近启动排序`() {
        val first = service.create("26.3")
        val second = service.create("26.2")

        // 都没启动过：按创建时间倒序（新的在前）
        assertEquals(listOf("26.2", "26.3"), service.recent().map { it.id })
        assertTrue(service.markLaunched(first.id))
        assertEquals(listOf("26.3", "26.2"), service.recent().map { it.id })
        assertTrue(service.recent().first().lastLaunchedAtEpochMillis > 0)
        assertFalse(service.markLaunched("missing"))
        assertEquals(second.id, service.list().first { it.id == second.id }.id)
        assertEquals(1, service.list().count { it.id == first.id })
    }

    @Test
    fun `列表按创建时间排序`() {
        service.create("26.3")
        service.create("26.2")

        assertEquals(listOf("26.3", "26.2"), service.list().map { it.versionId })
    }
}
