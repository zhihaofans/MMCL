package dev.mmcl.plugin.source.mojang

import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.capability.VersionManifestSource
import dev.mmcl.api.event.EventBus
import dev.mmcl.api.event.Subscription
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.service.ServiceRegistry
import dev.mmcl.api.service.SettingsService
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MojangVersionSourceTest {
    @Test
    fun `源元数据正确`() {
        val source = MojangVersionSource()

        assertEquals("mojang", source.id)
        assertEquals("Mojang 官方", source.displayName)
        assertEquals(100, source.priority)
        assertTrue(MojangVersionSource.DEFAULT_MANIFEST_URL.endsWith("version_manifest_v2.json"))
    }

    @Test
    fun `资源对象地址按 SHA-1 分段`() {
        val source = MojangVersionSource()
        val hash = "6a84d4afab797aaf9f6ad283ebcd5899493c5c64"

        assertEquals(
            "https://resources.download.minecraft.net/6a/$hash",
            source.assetObjectUrl(hash),
        )
        assertEquals(null, source.assetObjectUrl("6a"))
    }

    @Test
    fun `插件把源注册进服务表`() {
        val context = FakePluginContext()

        MojangSourcePlugin().onLoad(context)

        val sources = context.services.getAll(VersionManifestSource::class)
        assertEquals(1, sources.size)
        assertEquals("mojang", sources.single().id)
    }

    private class FakePluginContext : PluginContext {
        override val pluginId: String = "dev.mmcl.source.mojang.plugin"
        override val dataDir: Path = Path.of("build", "tmp", "source-mojang-data")
        override val logger = org.slf4j.LoggerFactory.getLogger("test.source.mojang")
        override val events: EventBus = NoopEventBus()
        override val services: ServiceRegistry = FakeServiceRegistry()
        override val settings: SettingsService = FakeSettings()
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

        override fun <T : Any> subscribe(type: KClass<T>, handler: (T) -> Unit): Subscription = Subscription { }
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

    private class FakeSettings : SettingsService {
        private val values = mutableMapOf<String, String>()

        override fun getString(key: String, default: String?): String? = values[key] ?: default

        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
