package dev.mmcl.auth.microsoft

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class XboxLiveClientTest {
    private val clock = { 1_000_000L }

    private val xblResponse =
        """
        {"Token":"xbl-token","DisplayClaims":{"xui":[{"uhs":"user-hash"}]}}
        """.trimIndent()

    private val xstsResponse =
        """
        {"Token":"xsts-token","DisplayClaims":{"xui":[{"uhs":"user-hash"}]}}
        """.trimIndent()

    private val loginResponse = """{"access_token":"mc-token","expires_in":86400}"""

    private val entitlementsResponse =
        """{"items":[{"name":"product_minecraft"},{"name":"game_minecraft"}]}"""

    private val profileResponse = """{"id":"0123456789abcdef0123456789abcdef","name":"Steve"}"""

    @Test
    fun `完整链路换出 Minecraft 会话`() = runTest {
        val transport =
            QueuedTransport(
                Reply.Body(xblResponse),
                Reply.Body(xstsResponse),
                Reply.Body(loginResponse),
                Reply.Body(entitlementsResponse),
                Reply.Body(profileResponse),
            )

        val session = XboxLiveClient(transport, clock).exchange("ms-token")

        assertEquals("0123456789abcdef0123456789abcdef", session.uuid)
        assertEquals("Steve", session.name)
        assertEquals("mc-token", session.accessToken)
        assertEquals(1_086_400L, session.expiresAtEpochSeconds)
        assertTrue(transport.calls.first().startsWith("JSON https://user.auth.xboxlive.com"))
        assertTrue(transport.calls.last().startsWith("GET https://api.minecraftservices.com/minecraft/profile"))
    }

    @Test
    fun `XSTS 无 Xbox 账号错误码映射成可提示原因`() = runTest {
        val transport =
            QueuedTransport(
                Reply.Body(xblResponse),
                Reply.Failure(401, """{"XErr":2148916233,"Message":"The account doesn't have an Xbox account"}"""),
            )

        val error = runCatching { XboxLiveClient(transport, clock).exchange("ms-token") }.exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.NO_XBOX_ACCOUNT, (error as MicrosoftAuthException).reason)
    }

    @Test
    fun `没有 Minecraft 档案时给出专门原因`() = runTest {
        val transport =
            QueuedTransport(
                Reply.Body(xblResponse),
                Reply.Body(xstsResponse),
                Reply.Body(loginResponse),
                Reply.Body(entitlementsResponse),
                Reply.Failure(404, """{"error":"NOT_FOUND"}"""),
            )

        val error = runCatching { XboxLiveClient(transport, clock).exchange("ms-token") }.exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.NO_MINECRAFT_PROFILE, (error as MicrosoftAuthException).reason)
    }

    @Test
    fun `Azure 应用未获权限时 403 映射成单独原因`() = runTest {
        val transport =
            QueuedTransport(
                Reply.Body(xblResponse),
                Reply.Body(xstsResponse),
                Reply.Failure(403, """{"error":"Invalid app registration"}"""),
            )

        val error = runCatching { XboxLiveClient(transport, clock).exchange("ms-token") }.exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.CLIENT_ID_NOT_APPROVED, (error as MicrosoftAuthException).reason)
    }

    @Test
    fun `没有游戏权益时在档案之前就失败`() = runTest {
        val transport =
            QueuedTransport(
                Reply.Body(xblResponse),
                Reply.Body(xstsResponse),
                Reply.Body(loginResponse),
                Reply.Body("""{"items":[]}"""),
            )

        val error = runCatching { XboxLiveClient(transport, clock).exchange("ms-token") }.exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.NO_GAME_ENTITLEMENT, (error as MicrosoftAuthException).reason)
        // 权益检查失败后不应再去拉档案
        assertTrue(transport.calls.none { it.contains("/minecraft/profile") })
    }

    @Test
    fun `Mojang 的 http 皮肤地址会升级为 https`() {
        assertEquals(
            "https://textures.minecraft.net/texture/abc",
            normalizeSkinUrl("http://textures.minecraft.net/texture/abc"),
        )
        assertEquals("http://example.invalid/skin.png", normalizeSkinUrl("http://example.invalid/skin.png"))
    }
}
