package dev.mmcl.plugin.hello

import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.api.event.EventBus
import dev.mmcl.api.event.Subscription
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.service.ServiceRegistry
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HelloPluginTest {
    @Test
    fun `onLoad 向服务注册表登记 AuthProvider`() {
        val context = FakePluginContext()
        val plugin = HelloPlugin()

        plugin.onLoad(context)

        val providers = context.services.getAll(AuthProvider::class)
        assertEquals(1, providers.size)
        assertNotNull(context.services.get(AuthProvider::class))
    }

    @Test
    fun `enable 后 disable 幂等且未初始化时也不抛异常`() {
        val plugin = HelloPlugin()

        plugin.onDisable()
        plugin.onLoad(FakePluginContext())
        plugin.onEnable()
        plugin.onDisable()
        plugin.onDisable()
    }

    private class FakePluginContext : PluginContext {
        override val pluginId: String = "dev.mmcl.plugin.hello"
        override val dataDir: Path = Path.of("build", "tmp", "hello-plugin-data")
        override val logger = org.slf4j.LoggerFactory.getLogger("test.hello")
        override val events: EventBus = NoopEventBus()
        override val services: ServiceRegistry = FakeServiceRegistry()
        override val launcher: LauncherInfo =
            LauncherInfo(
                name = "MMCL",
                version = "0.0.1",
                os = OperatingSystem.MACOS,
                arch = Architecture.ARM64,
                dataDir = dataDir,
            )
    }

    private class NoopEventBus : EventBus {
        override fun <T : Any> publish(event: T) = Unit

        override fun <T : Any> subscribe(type: KClass<T>, handler: (T) -> Unit): Subscription =
            Subscription { }
    }

    private class FakeServiceRegistry : ServiceRegistry {
        private val store = mutableMapOf<KClass<*>, MutableList<Any>>()

        override fun <T : Any> register(type: KClass<T>, instance: T) {
            store.getOrPut(type) { mutableListOf() }.add(instance)
        }

        override fun <T : Any> get(type: KClass<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return store[type]?.lastOrNull() as T?
        }

        override fun <T : Any> getAll(type: KClass<T>): List<T> {
            @Suppress("UNCHECKED_CAST")
            return store[type]?.map { it as T }.orEmpty()
        }
    }
}
