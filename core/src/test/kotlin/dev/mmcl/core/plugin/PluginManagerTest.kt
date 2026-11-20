package dev.mmcl.core.plugin

import dev.mmcl.api.ApiLevel
import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.Account
import dev.mmcl.api.capability.AuthProvider
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.mmcl.core.settings.InMemorySettingsService
import org.slf4j.LoggerFactory

class PluginManagerTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var dataDir: Path

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("mmcl-plugin-test")
    }

    @AfterTest
    fun tearDown() {
        dataDir.toFile().deleteRecursively()
    }

    private fun launcherInfo() =
        LauncherInfo(
            name = "MMCL",
            version = "0.0.1",
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
            dataDir = dataDir,
        )

    private fun descriptor(plugin: FakePlugin) =
        PluginDescriptor(
            id = plugin.id,
            version = plugin.version,
            dependencies = plugin.dependencies,
            optionalDependencies = plugin.optionalDependencies,
            plugin = plugin,
        )

    @Test
    fun `插件上下文暴露当前语言`() {
        var seen: String? = null
        val plugin = FakePlugin("locale-probe", onLoadHook = { context -> seen = context.locale })
        val settings = InMemorySettingsService().also { it.putString("ui.locale", "zh-CN") }
        val manager = PluginManager(launcherInfo(), logger, settings = settings)

        manager.start(listOf(descriptor(plugin)))

        assertEquals("zh-CN", seen)
        manager.close()
    }

    @Test
    fun `禁用的插件不执行生命周期`() {
        val plugin = FakePlugin("disabled-plugin")
        val manager = PluginManager(launcherInfo(), logger)

        manager.start(listOf(descriptor(plugin)), disabledIds = setOf("disabled-plugin"))

        assertEquals(PluginState.DISABLED, manager.plugins.single().state)
        assertEquals(0, plugin.loadCount)
        assertEquals(0, plugin.enableCount)
        manager.close()
    }

    @Test
    fun `依赖被禁用插件的插件按缺失依赖处理`() {
        val disabled = FakePlugin("disabled-dep")
        val dependent = FakePlugin("needs-disabled", dependencies = listOf("disabled-dep"))
        val manager = PluginManager(launcherInfo(), logger)

        manager.start(listOf(descriptor(disabled), descriptor(dependent)), setOf("disabled-dep"))

        assertEquals(PluginState.DISABLED, manager.plugins.first { it.id == "disabled-dep" }.state)
        assertEquals(PluginState.MISSING_DEP, manager.plugins.first { it.id == "needs-disabled" }.state)
        assertEquals(0, dependent.loadCount)
        manager.close()
    }

    @Test
    fun `插件元信息有默认值`() {
        val plugin = FakePlugin("plain")

        assertEquals("plain", plugin.title, "未提供标题时用 id")
        assertEquals("", plugin.author)
        assertEquals(PluginIcon.DEFAULT, plugin.icon)
    }

    @Test
    fun `声明超前 API 的插件被拒绝加载`() {
        val plugin = FakePlugin("future", apiLevel = ApiLevel.CURRENT + 1)
        val manager = PluginManager(launcherInfo(), logger)

        manager.start(listOf(descriptor(plugin)))

        val loaded = manager.plugins.single()
        assertEquals(PluginState.INCOMPATIBLE_API, loaded.state)
        assertEquals(0, plugin.loadCount, "被拒绝的插件不应执行 onLoad")
        manager.close()
    }

    @Test
    fun `未声明 API 级别的旧插件仍可加载`() {
        val plugin = FakePlugin("legacy", apiLevel = 0)
        val manager = PluginManager(launcherInfo(), logger)

        manager.start(listOf(descriptor(plugin)))

        assertEquals(PluginState.ENABLED, manager.plugins.single().state)
        assertEquals(1, plugin.loadCount)
        manager.close()
    }

    @Test
    fun `依赖被拒插件的插件按缺失依赖处理`() {
        val future = FakePlugin("future", apiLevel = ApiLevel.CURRENT + 1)
        val dependent = FakePlugin("dependent", dependencies = listOf("future"))
        val manager = PluginManager(launcherInfo(), logger)

        manager.start(listOf(descriptor(future), descriptor(dependent)))

        val byId = manager.plugins.associateBy { it.id }
        assertEquals(PluginState.INCOMPATIBLE_API, byId.getValue("future").state)
        assertEquals(PluginState.MISSING_DEP, byId.getValue("dependent").state)
        assertEquals(0, dependent.loadCount, "依赖不可用时不应执行 onLoad")
        manager.close()
    }

    @Test
    fun `onLoad 抛异常不会影响其它插件`() {
        val bad = FakePlugin("bad", onLoadHook = { error("boom") })
        val good = FakePlugin("good")

        PluginManager(launcherInfo(), logger).use { manager ->
            manager.start(listOf(descriptor(bad), descriptor(good)))

            val states = manager.plugins.associate { it.id to it.state }
            assertEquals(PluginState.FAILED, states["bad"])
            assertEquals(PluginState.ENABLED, states["good"])
            assertEquals(1, bad.loadCount)
            assertEquals(1, good.enableCount)
        }
    }

    @Test
    fun `onEnable 抛异常不会影响其它插件`() {
        val bad = FakePlugin("bad", onEnableHook = { error("boom") })
        val good = FakePlugin("good")

        PluginManager(launcherInfo(), logger).use { manager ->
            manager.start(listOf(descriptor(bad), descriptor(good)))

            val states = manager.plugins.associate { it.id to it.state }
            assertEquals(PluginState.FAILED, states["bad"])
            assertEquals(PluginState.ENABLED, states["good"])
        }
    }

    @Test
    fun `缺失依赖的插件不会被加载`() {
        val orphan = FakePlugin("orphan", dependencies = listOf("ghost"))

        PluginManager(launcherInfo(), logger).use { manager ->
            manager.start(listOf(descriptor(orphan)))

            assertEquals(PluginState.MISSING_DEP, manager.plugins.single().state)
            assertEquals(0, orphan.loadCount)
        }
    }

    @Test
    fun `循环依赖的插件不会被加载`() {
        val first = FakePlugin("first", dependencies = listOf("second"))
        val second = FakePlugin("second", dependencies = listOf("first"))

        PluginManager(launcherInfo(), logger).use { manager ->
            manager.start(listOf(descriptor(first), descriptor(second)))

            assertTrue(manager.plugins.all { it.state == PluginState.CYCLE })
            assertEquals(0, first.loadCount + second.loadCount)
        }
    }

    @Test
    fun `插件会拿到独立的 dataDir 且目录已创建`() {
        val plugin = FakePlugin("dev.mmcl.test")

        PluginManager(launcherInfo(), logger).use { manager ->
            manager.start(listOf(descriptor(plugin)))

            val context = plugin.context ?: error("context 未注入")
            assertEquals(dataDir.resolve("plugin-data").resolve("dev.mmcl.test"), context.dataDir)
            assertTrue(Files.isDirectory(context.dataDir))
            assertEquals("0.0.1", context.launcher.version)
        }
    }

    @Test
    fun `插件注册的服务可以被消费`() {
        val provider =
            object : AuthProvider {
                override val id: String = "offline"

                override suspend fun login(): Account = Account(uuid = "uuid", name = "Steve")
            }
        val plugin =
            FakePlugin(
                "dev.mmcl.auth.offline",
                onLoadHook = { context -> context.services.register(AuthProvider::class, provider) },
            )

        PluginManager(launcherInfo(), logger).use { manager ->
            manager.start(listOf(descriptor(plugin)))

            assertEquals(listOf(provider), manager.services.getAll(AuthProvider::class))
        }
    }

    @Test
    fun `关闭时按启用顺序逆序禁用且幂等`() {
        val order = mutableListOf<String>()
        val first = FakePlugin("a", onDisableHook = { order += "a" })
        val second = FakePlugin("b", dependencies = listOf("a"), onDisableHook = { order += "b" })
        val manager = PluginManager(launcherInfo(), logger)

        manager.start(listOf(descriptor(second), descriptor(first)))
        manager.close()
        manager.close()

        assertEquals(listOf("b", "a"), order)
        assertEquals(1, first.disableCount)
        assertEquals(1, second.disableCount)
        assertTrue(manager.plugins.all { it.state == PluginState.DISABLED })
    }
}
