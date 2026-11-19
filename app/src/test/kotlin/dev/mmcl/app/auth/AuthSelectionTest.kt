package dev.mmcl.app.auth

import dev.mmcl.api.capability.Account
import dev.mmcl.api.capability.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthSelectionTest {
    private fun provider(
        id: String,
        priority: Int,
        signedIn: Boolean,
    ) = object : AuthProvider {
        override val id: String = id
        override val priority: Int = priority
        override val isSignedIn: Boolean = signedIn

        override suspend fun login(): Account = Account(uuid = "", name = id)
    }

    private val microsoft = provider("microsoft", priority = 100, signedIn = true)
    private val offline = provider("offline", priority = 1000, signedIn = true)

    @Test
    fun `自动模式按优先级选已登录账户`() {
        assertEquals("microsoft", listOf(microsoft, offline).activeAccount(AUTO_PROVIDER)?.id)
        assertEquals("microsoft", listOf(microsoft, offline).activeAccount(null)?.id)
    }

    @Test
    fun `指定离线账户时优先离线`() {
        assertEquals("offline", listOf(microsoft, offline).activeAccount("offline")?.id)
    }

    @Test
    fun `指定的账户未登录时回退到可用账户`() {
        val signedOut = provider("microsoft", priority = 100, signedIn = false)

        assertEquals("offline", listOf(signedOut, offline).activeAccount("microsoft")?.id)
    }

    @Test
    fun `没有已登录账户时返回 null`() {
        val a = provider("microsoft", priority = 100, signedIn = false)
        val b = provider("offline", priority = 1000, signedIn = false)

        assertNull(listOf(a, b).activeAccount(null))
        assertNull(listOf(a, b).activeAccount("offline"))
    }
}
