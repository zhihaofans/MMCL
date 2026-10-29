package dev.mmcl.core.service

import dev.mmcl.api.capability.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DefaultServiceRegistryTest {
    private val registry = DefaultServiceRegistry()

    private fun provider(id: String) =
        object : AuthProvider {
            override val id: String = id

            override suspend fun login() = dev.mmcl.api.capability.Account(uuid = "uuid-$id", name = id)
        }

    @Test
    fun `get 返回最后注册的实例`() {
        val first = provider("offline")
        val second = provider("microsoft")
        registry.register(AuthProvider::class, first)
        registry.register(AuthProvider::class, second)

        assertSame(second, registry.get(AuthProvider::class))
    }

    @Test
    fun `getAll 按注册顺序返回全部实例`() {
        val first = provider("offline")
        val second = provider("microsoft")
        registry.register(AuthProvider::class, first)
        registry.register(AuthProvider::class, second)

        assertEquals(listOf(first, second), registry.getAll(AuthProvider::class))
    }

    @Test
    fun `未注册时返回 null 与空列表`() {
        assertNull(registry.get(AuthProvider::class))
        assertEquals(emptyList(), registry.getAll(AuthProvider::class))
    }
}
