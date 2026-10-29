package dev.mmcl.plugin.theme.sakura

import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.event.EventBus
import dev.mmcl.api.event.Subscription
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.service.ServiceRegistry
import dev.mmcl.api.service.SettingsService
import dev.mmcl.api.ui.LauncherTheme
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SakuraThemeTest {
    @Test
    fun `主题元数据正确`() {
        val theme = SakuraTheme()

        assertEquals("dev.mmcl.theme.sakura", theme.id)
        assertEquals("樱花", theme.displayName)
        assertFalse(theme.dark)
    }

    @Test
    fun `插件把主题注册进服务表`() {
        val context = FakePluginContext()

        SakuraThemePlugin().onLoad(context)

        val themes = context.services.getAll(LauncherTheme::class)
        assertEquals(1, themes.size)
        assertEquals("dev.mmcl.theme.sakura", themes.single().id)
    }

    private class FakePluginContext : PluginContext {
        override val pluginId: String = "dev.mmcl.theme.sakura.plugin"
        override val dataDir: Path = Path.of("build", "tmp", "theme-sakura-data")
        override val logger = org.slf4j.LoggerFactory.getLogger("test.theme.sakura")
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
