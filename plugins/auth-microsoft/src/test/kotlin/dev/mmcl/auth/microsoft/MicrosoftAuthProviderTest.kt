package dev.mmcl.auth.microsoft

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class MicrosoftAuthProviderTest {
    private val logger = LoggerFactory.getLogger("test.microsoft-provider")
    private val clock = { 1_000_000L }

    private val xblResponse = """{"Token":"xbl","DisplayClaims":{"xui":[{"uhs":"uhs"}]}}"""
    private val xstsResponse = """{"Token":"xsts","DisplayClaims":{"xui":[{"uhs":"uhs"}]}}"""
    private val loginResponse = """{"access_token":"mc","expires_in":86400}"""
    private val entitlementsResponse = """{"items":[{"name":"game_minecraft"}]}"""
    private val profileResponse = """{"id":"00000000000000000000000000000001","name":"Alex"}"""

    private fun store(dir: java.nio.file.Path = Files.createTempDirectory("mmcl-auth")): TokenStore =
        TokenStore(dir.resolve("tokens.properties"), logger)

    private fun provider(
        store: TokenStore,
        transport: HttpTransport = QueuedTransport(),
        clientId: String? = "test-client",
    ): MicrosoftAuthProvider {
        val oauthFactory: () -> MicrosoftOAuthClient? =
            { clientId?.let { MicrosoftOAuthClient(it, transport, now = clock) } }
        return MicrosoftAuthProvider(
            tokenStore = store,
            oauth = oauthFactory,
            xbox = XboxLiveClient(transport, clock),
            logger = logger,
            now = clock,
        )
    }

    @Test
    fun `没有登录态时未登录且启动会提示`() = runTest {
        val provider = provider(store())

        assertTrue(!provider.isSignedIn)
        assertNull(provider.accountOrNull())

        val error = runCatching { provider.createSession("Player") }.exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.NOT_SIGNED_IN, (error as MicrosoftAuthException).reason)
    }

    @Test
    fun `设备码完成登录后保存账号并返回 msa 类型`() = runTest {
        val store = store()
        val transport =
            QueuedTransport(
                Reply.Body(xblResponse),
                Reply.Body(xstsResponse),
                Reply.Body(loginResponse),
                Reply.Body(entitlementsResponse),
                Reply.Body(profileResponse),
            )
        val provider = provider(store, transport)

        val account = provider.completeLogin(MicrosoftTokens("ms-token", "refresh-1", 1_003_600))

        assertEquals("Alex", account.name)
        assertEquals("00000000000000000000000000000001", account.uuid)
        assertEquals("mc", account.accessToken)
        assertEquals("msa", account.userType)
        assertTrue(provider.isSignedIn)
        assertEquals("refresh-1", store.load()?.refreshToken)
    }

    @Test
    fun `Minecraft 令牌未过期时直接用缓存不发请求`() = runTest {
        val store = store()
        store.save(StoredTokens("refresh-1", "mc-cached", 1_000_000L + 3_600, "uuid-1", "Steve"))
        val transport = QueuedTransport()
        val provider = provider(store, transport)

        val account = provider.createSession("ignored")

        assertEquals("Steve", account.name)
        assertEquals("mc-cached", account.accessToken)
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `令牌过期时用刷新令牌换新并写回`() = runTest {
        val store = store()
        store.save(StoredTokens("old-refresh", "mc-stale", 1_000L, "00000000000000000000000000000001", "Alex"))
        val transport =
            QueuedTransport(
                Reply.Body("""{"access_token":"ms-new","refresh_token":"new-refresh","expires_in":3600}"""),
                Reply.Body(xblResponse),
                Reply.Body(xstsResponse),
                Reply.Body(loginResponse),
                Reply.Body(entitlementsResponse),
                Reply.Body(profileResponse),
            )
        val provider = provider(store, transport)

        val account = provider.createSession("ignored")

        assertEquals("mc", account.accessToken)
        assertEquals("new-refresh", store.load()?.refreshToken)
        assertEquals(1_000_000L + 86_400, store.load()?.accessTokenExpiresAt)
        assertTrue(transport.calls.first().startsWith("FORM"))
    }

    @Test
    fun `刷新后账号与本地不一致时清空并要求重登`() = runTest {
        val store = store()
        store.save(StoredTokens("old-refresh", "mc-stale", 1_000L, "ffffffffffffffffffffffffffffffff", "Notch"))
        val transport =
            QueuedTransport(
                Reply.Body("""{"access_token":"ms-new","refresh_token":"new-refresh","expires_in":3600}"""),
                Reply.Body(xblResponse),
                Reply.Body(xstsResponse),
                Reply.Body(loginResponse),
                Reply.Body(entitlementsResponse),
                Reply.Body(profileResponse),
            )
        val provider = provider(store, transport)

        val error = runCatching { provider.createSession("ignored") }.exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.ACCOUNT_MISMATCH, (error as MicrosoftAuthException).reason)
        assertNull(store.load())
    }

    @Test
    fun `没配 client id 且令牌过期时报缺配置`() = runTest {
        val store = store()
        store.save(StoredTokens("old-refresh", null, 1_000L, null, null))
        val provider = provider(store, QueuedTransport(), clientId = null)

        val error = runCatching { provider.createSession("ignored") }.exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.NO_CLIENT_ID, (error as MicrosoftAuthException).reason)
    }

    @Test
    fun `退出登录只清本地令牌`() {
        val store = store()
        store.save(StoredTokens("refresh-1", "mc", 1_000_000L + 3_600, "uuid", "Steve"))
        val provider = provider(store, QueuedTransport())

        provider.signOut()

        assertNull(store.load())
        assertTrue(!provider.isSignedIn)
    }
}
