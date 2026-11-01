package dev.mmcl.plugin.ui.about

import dev.mmcl.api.Architecture
import dev.mmcl.api.LauncherInfo
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.event.EventBus
import dev.mmcl.api.event.Subscription
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.service.ServiceRegistry
import dev.mmcl.api.service.SettingsService
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiPage
import dev.mmcl.api.ui.UiSlot
import dev.mmcl.api.ui.UiWidget
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class AboutPluginTest {
    @Test
    fun `注册一个 TOOLS 分组的页面`() {
        val context = FakePluginContext()

        AboutPlugin().onLoad(context)

        val pages = context.services.getAll(UiPage::class)
        assertEquals(1, pages.size)
        assertEquals("dev.mmcl.ui.about", pages.single().id)
        assertEquals(UiGroup.TOOLS, pages.single().group)
    }

    @Test
    fun `注册首页卡片与状态栏小部件`() {
        val context = FakePluginContext()

        AboutPlugin().onLoad(context)

        val widgets = context.services.getAll(UiWidget::class)
        assertEquals(2, widgets.size)
        assertEquals(
            setOf(UiSlot.HOME, UiSlot.STATUS_BAR),
            widgets.map { it.slot }.toSet(),
        )
    }

    private class FakePluginContext : PluginContext {
        override val pluginId: String = "dev.mmcl.ui.about.plugin"
        override val dataDir: Path = Path.of("build", "tmp", "ui-about-data")
        override val logger = org.slf4j.LoggerFactory.getLogger("test.ui.about")
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
