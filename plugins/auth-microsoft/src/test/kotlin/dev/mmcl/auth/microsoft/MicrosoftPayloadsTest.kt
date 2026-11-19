package dev.mmcl.auth.microsoft

import kotlin.test.Test
import kotlin.test.assertContains
import kotlinx.serialization.encodeToString

class MicrosoftPayloadsTest {
    @Test
    fun `XBL 请求包含协议必需字段`() {
        val body = MicrosoftJson.encodeToString(XblRequest(properties = XblProperties(rpsTicket = "d=token")))

        assertContains(body, "\"RelyingParty\":\"http://auth.xboxlive.com\"")
        assertContains(body, "\"TokenType\":\"JWT\"")
        assertContains(body, "\"AuthMethod\":\"RPS\"")
        assertContains(body, "\"SiteName\":\"user.auth.xboxlive.com\"")
        assertContains(body, "\"RpsTicket\":\"d=token\"")
    }

    @Test
    fun `XSTS 请求包含 SandboxId 与 RelyingParty`() {
        val body = MicrosoftJson.encodeToString(XstsRequest(properties = XstsProperties(userTokens = listOf("xbl"))))

        assertContains(body, "\"SandboxId\":\"RETAIL\"")
        assertContains(body, "\"RelyingParty\":\"rp://api.minecraftservices.com/\"")
        assertContains(body, "\"UserTokens\":[\"xbl\"]")
    }
}
