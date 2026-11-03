package dev.mmcl.auth.microsoft

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class MicrosoftLoginServiceTest {
    private val logger = LoggerFactory.getLogger("test.microsoft-login")
    private val clock = { 1_000_000L }

    private val deviceCodeReply =
        Reply.Body(
            """{"device_code":"dev","user_code":"ABCD-EFGH","verification_uri":"https://microsoft.com/link","expires_in":900,"interval":5}""",
        )
    private val xblResponse = """{"Token":"xbl","DisplayClaims":{"xui":[{"uhs":"uhs"}]}}"""
    private val xstsResponse = """{"Token":"xsts","DisplayClaims":{"xui":[{"uhs":"uhs"}]}}"""
    private val loginResponse = """{"access_token":"mc","expires_in":86400}"""
    private val entitlementsResponse = """{"items":[{"name":"game_minecraft"}]}"""
    private val profileResponse = """{"id":"00000000000000000000000000000001","name":"Alex"}"""

    private fun tempStore(): TokenStore =
        TokenStore(Files.createTempDirectory("mmcl-login").resolve("tokens.properties"), logger)

    private fun provider(store: TokenStore, transport: HttpTransport, clientId: String?): MicrosoftAuthProvider =
        MicrosoftAuthProvider(
            tokenStore = store,
            oauth = { clientId?.let { MicrosoftOAuthClient(it, transport, now = clock, sleep = {}) } },
            xbox = XboxLiveClient(transport, clock),
            logger = logger,
            now = clock,
        )

    @Test
    fun `没有 client id 时直接给出原因`() = runTest {
        val transport = QueuedTransport()
        val service =
            MicrosoftLoginService(
                provider = provider(tempStore(), transport, null),
                oauthFactory = { null },
                scope = this,
                logger = logger,
                now = clock,
            )

        service.beginLogin()

        assertEquals(
            LoginState.Failed(MicrosoftAuthException.Reason.NO_CLIENT_ID, null),
            service.state.value,
        )
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `成功登录后状态变为已登录并写入令牌`() = runTest {
        val store = tempStore()
        val transport =
            QueuedTransport(
                deviceCodeReply,
                Reply.Body("""{"access_token":"ms","refresh_token":"refresh-1","expires_in":3600}"""),
                Reply.Body(xblResponse),
                Reply.Body(xstsResponse),
                Reply.Body(loginResponse),
                Reply.Body(entitlementsResponse),
                Reply.Body(profileResponse),
            )
        val service =
            MicrosoftLoginService(
                provider = provider(store, transport, "client"),
                oauthFactory = { MicrosoftOAuthClient("client", transport, now = clock, sleep = {}) },
                scope = this,
                logger = logger,
                now = clock,
            )

        service.beginLogin()
        advanceUntilIdle()

        val state = service.state.value
        assertTrue(state is LoginState.SignedIn, "状态应为已登录：$state")
        assertEquals("Alex", (state as LoginState.SignedIn).account.name)
        assertEquals("refresh-1", store.load()?.refreshToken)
    }

    @Test
    fun `设备码过期时状态为失败并可重试`() = runTest {
        val transport =
            QueuedTransport(
                deviceCodeReply,
                Reply.Body("""{"error":"expired_token"}"""),
            )
        val service =
            MicrosoftLoginService(
                provider = provider(tempStore(), transport, "client"),
                oauthFactory = { MicrosoftOAuthClient("client", transport, now = clock, sleep = {}) },
                scope = this,
                logger = logger,
                now = clock,
            )

        service.beginLogin()
        advanceUntilIdle()

        val state = service.state.value
        assertTrue(state is LoginState.Failed, "状态应为失败：$state")
        assertEquals(
            MicrosoftAuthException.Reason.DEVICE_CODE_EXPIRED,
            (state as LoginState.Failed).reason,
        )
    }

    @Test
    fun `退出登录清空本地令牌并回到未登录`() = runTest {
        val store = tempStore()
        store.save(StoredTokens("refresh-1", "mc", 1_000_000L + 3600, "uuid", "Alex"))
        val service =
            MicrosoftLoginService(
                provider = provider(store, QueuedTransport(), "client"),
                oauthFactory = { null },
                scope = this,
                logger = logger,
                now = clock,
            )

        assertTrue(service.state.value is LoginState.SignedIn)
        service.signOut()

        assertEquals(LoginState.Idle, service.state.value)
        assertEquals(null, store.load())
    }
}
