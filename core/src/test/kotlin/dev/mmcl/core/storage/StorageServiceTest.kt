package dev.mmcl.core.storage

import dev.mmcl.core.instance.Instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class StorageServiceTest {
    private val logger = LoggerFactory.getLogger("test.storage")
    private lateinit var root: Path

    private val instances =
        listOf(
            Instance(id = "a", name = "模组A", versionId = "fabric-loader-0.20.1-26.3"),
            Instance(id = "b", name = "原版", versionId = "26.2"),
        )

    private fun service() = StorageService(root, logger) { instances }

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("mmcl-storage-test")
        write("instances/a/mods/a.jar", 10)
        write("instances/b/saves/world.dat", 5)
        writeText("versions/26.3/26.3.json", """{"id":"26.3"}""")
        write("versions/26.3/26.3.jar", 100)
        writeText(
            "versions/fabric-loader-0.20.1-26.3/fabric-loader-0.20.1-26.3.json",
            """{"id":"fabric-loader-0.20.1-26.3","inheritsFrom":"26.3"}""",
        )
        write("versions/fabric-loader-0.20.1-26.3/loader.jar", 20)
        writeText("versions/26.2/26.2.json", """{"id":"26.2"}""")
        write("versions/26.2/26.2.jar", 30)
        writeText("versions/26.1/26.1.json", """{"id":"26.1"}""")
        write("libraries/com/example/lib.jar", 40)
        write("assets/objects/ab/abc", 50)
        write("cache/skins/a.png", 60)
        write("logs/latest.log", 70)
        write("plugins/foo.jar", 90)
        write("runtime/macos-arm64/jdk-25/bin/java", 80)
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private fun write(relative: String, bytes: Int) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.write(file, ByteArray(bytes) { 'x'.code.toByte() })
    }

    private fun writeText(relative: String, text: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    @Test
    fun `扫描分类与版本引用关系`() {
        val snapshot = service().scan()

        val categories = snapshot.categories.associate { it.id to it.bytes }
        assertEquals(categories.values.sum(), snapshot.totalBytes)
        assertEquals(15L, categories[StorageCategoryId.INSTANCES])
        assertEquals(40L, categories[StorageCategoryId.LIBRARIES])
        assertEquals(50L, categories[StorageCategoryId.ASSETS])
        assertEquals(60L, categories[StorageCategoryId.CACHE])
        assertEquals(70L, categories[StorageCategoryId.LOGS])
        assertEquals(90L, categories[StorageCategoryId.PLUGINS])
        assertEquals(80L, categories[StorageCategoryId.RUNTIME])
    }

    @Test
    fun `游戏版本与加载器版本分开且带引用保护`() {
        val snapshot = service().scan()

        assertEquals(listOf("26.1", "26.2", "26.3"), snapshot.gameVersions.map { it.id })
        assertEquals(listOf("fabric-loader-0.20.1-26.3"), snapshot.loaderVersions.map { it.id })

        val base = snapshot.gameVersions.first { it.id == "26.3" }
        assertEquals(VersionBlock.INHERITED_BY, base.blocked)
        assertEquals(listOf("fabric-loader-0.20.1-26.3"), base.inheritedBy)

        val used = snapshot.gameVersions.first { it.id == "26.2" }
        assertEquals(VersionBlock.USED_BY_INSTANCE, used.blocked)
        assertEquals(listOf("b"), used.usedByInstanceIds)

        val loader = snapshot.loaderVersions.single()
        assertEquals(VersionBlock.USED_BY_INSTANCE, loader.blocked)
        assertEquals("26.3", loader.baseVersionId)

        val unused = snapshot.gameVersions.first { it.id == "26.1" }
        assertTrue(unused.deletable)
    }

    @Test
    fun `运行时识别主版本与保护状态`() {
        val runtimePath = root.resolve("runtime/macos-arm64/jdk-25")
        val snapshot = service().scan(protectedRuntimes = setOf(runtimePath))

        val runtime = snapshot.runtimes.single()
        assertEquals("macos-arm64/jdk-25", runtime.relativePath)
        assertEquals(25, runtime.major)
        assertTrue(runtime.protected)
    }

    @Test
    fun `删除版本前重新检查引用`() {
        val service = service()

        assertEquals(DeleteBlock.INHERITED_BY, (service.deleteVersion("26.3") as DeleteOutcome.Blocked).reason)
        assertEquals(DeleteBlock.USED_BY_INSTANCE, (service.deleteVersion("26.2") as DeleteOutcome.Blocked).reason)
        assertEquals(DeleteBlock.USED_BY_INSTANCE, (service.deleteVersion("fabric-loader-0.20.1-26.3") as DeleteOutcome.Blocked).reason)
        assertEquals(DeleteBlock.INVALID_TARGET, (service.deleteVersion("../26.3") as DeleteOutcome.Blocked).reason)
        assertTrue(Files.isDirectory(root.resolve("versions/26.3")))

        val deleted = service.deleteVersion("26.1") as DeleteOutcome.Deleted
        assertTrue(deleted.bytes > 0)
        assertFalse(Files.exists(root.resolve("versions/26.1")))
    }

    @Test
    fun `运行中的版本不能删除`() {
        val outcome = service().deleteVersion("26.1", runningVersionIds = setOf("26.1"))

        assertEquals(DeleteBlock.RUNNING, (outcome as DeleteOutcome.Blocked).reason)
        assertTrue(Files.isDirectory(root.resolve("versions/26.1")))
    }

    @Test
    fun `删除运行时保护与路径校验`() {
        val service = service()
        val runtimePath = root.resolve("runtime/macos-arm64/jdk-25")

        assertEquals(
            DeleteBlock.PROTECTED,
            (service.deleteRuntime("macos-arm64/jdk-25", setOf(runtimePath)) as DeleteOutcome.Blocked).reason,
        )
        assertEquals(
            DeleteBlock.INVALID_TARGET,
            (service.deleteRuntime("../escape") as DeleteOutcome.Blocked).reason,
        )
        assertEquals(
            DeleteBlock.INVALID_TARGET,
            (service.deleteRuntime("macos-arm64/jdk-25/../../x") as DeleteOutcome.Blocked).reason,
        )

        val deleted = service.deleteRuntime("macos-arm64/jdk-25") as DeleteOutcome.Deleted
        assertEquals(80, deleted.bytes.toInt())
        assertFalse(Files.exists(runtimePath))
    }

    @Test
    fun `清理缓存只删内容`() {
        val outcome = service().clearCache() as DeleteOutcome.Deleted

        assertEquals(60, outcome.bytes.toInt())
        assertTrue(Files.isDirectory(root.resolve("cache")))
        assertFalse(Files.exists(root.resolve("cache/skins/a.png")))
    }
}
