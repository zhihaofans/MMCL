package dev.mmcl.auth.microsoft

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MicrosoftOAuthClientTest {
    private val clock = { 1_000_000L }

    private fun client(transport: HttpTransport) =
        MicrosoftOAuthClient(clientId = "test-client", transport = transport, now = clock)

    @Test
    fun `设备码响应解析成挑战`() = runTest {
        val transport =
            QueuedTransport(
                Reply.Body(
                    """
                    {"device_code":"dev-123","user_code":"ABCD-EFGH",
                     "verification_uri":"https://microsoft.com/link","expires_in":900,"interval":5}
                    """.trimIndent(),
                ),
            )

        val challenge = client(transport).requestDeviceCode()

        assertEquals("ABCD-EFGH", challenge.userCode)
        assertEquals("https://microsoft.com/link", challenge.verificationUri)
        assertEquals("dev-123", challenge.deviceCode)
        assertEquals(900, challenge.expiresInSeconds)
        assertEquals(1_000_900L, challenge.expiresAt(1_000_000L))
    }

    @Test
    fun `轮询 authorization_pending 与 slow_down 都算未完成`() = runTest {
        val pending = client(QueuedTransport(Reply.Body("""{"error":"authorization_pending"}""")))
        assertEquals(DeviceCodePoll.Pending(slowDown = false), pending.poll("dev"))

        val slow = client(QueuedTransport(Reply.Body("""{"error":"slow_down"}""")))
        assertEquals(DeviceCodePoll.Pending(slowDown = true), slow.poll("dev"))
    }

    @Test
    fun `设备码过期与用户拒绝映射到不同原因`() = runTest {
        val expired = client(QueuedTransport(Reply.Body("""{"error":"expired_token"}""")))
        val expiredPoll = expired.poll("dev")
        assertEquals(
            MicrosoftAuthException.Reason.DEVICE_CODE_EXPIRED,
            (expiredPoll as DeviceCodePoll.Failed).reason,
        )

        val declined = client(QueuedTransport(Reply.Body("""{"error":"access_denied"}""")))
        val declinedPoll = declined.poll("dev")
        assertEquals(
            MicrosoftAuthException.Reason.AUTHORIZATION_DECLINED,
            (declinedPoll as DeviceCodePoll.Failed).reason,
        )
    }

    @Test
    fun `拿到令牌时按 expires_in 计算到期时间`() = runTest {
        val transport =
            QueuedTransport(
                Reply.Body(
                    """{"access_token":"ms-token","refresh_token":"refresh-1","expires_in":3600}""",
                ),
            )

        val poll = client(transport).poll("dev")

        val tokens = (poll as DeviceCodePoll.Granted).tokens
        assertEquals("ms-token", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals(1_003_600L, tokens.expiresAtEpochSeconds)
    }

    @Test
    fun `轮询会跳过 pending 直到成功并响应 slow_down`() = runTest {
        val sleeps = mutableListOf<Long>()
        val client =
            MicrosoftOAuthClient(
                clientId = "test-client",
                transport =
                    QueuedTransport(
                        Reply.Body("""{"error":"authorization_pending"}"""),
                        Reply.Body("""{"error":"slow_down"}"""),
                        Reply.Body("""{"access_token":"ms","refresh_token":"r","expires_in":60}"""),
                    ),
                now = clock,
                sleep = { sleeps += it },
            )

        val tokens = client.awaitDeviceCode("dev", intervalSeconds = 2, deadlineEpochSeconds = 2_000_000)

        assertEquals("ms", tokens.accessToken)
        // 第一次 2 秒，slow_down 之后间隔 +5
        assertEquals(listOf(2L, 2L, 7L), sleeps)
    }

    @Test
    fun `轮询超过截止时间抛设备码过期`() = runTest {
        val client =
            MicrosoftOAuthClient(
                clientId = "test-client",
                transport = QueuedTransport(),
                now = { 2_000_000L },
                sleep = {},
            )

        val error =
            runCatching { client.awaitDeviceCode("dev", 5, deadlineEpochSeconds = 1_999_000) }
                .exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.DEVICE_CODE_EXPIRED, (error as MicrosoftAuthException).reason)
    }

    @Test
    fun `刷新失败映射为未登录`() = runTest {
        val transport = QueuedTransport(Reply.Body("""{"error":"invalid_grant","error_description":"expired"}"""))

        val error = runCatching { client(transport).refresh("old") }.exceptionOrNull()

        assertTrue(error is MicrosoftAuthException)
        assertEquals(MicrosoftAuthException.Reason.NOT_SIGNED_IN, (error as MicrosoftAuthException).reason)
    }
}
