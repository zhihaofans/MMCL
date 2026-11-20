package dev.mmcl.auth.microsoft

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
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
    fun `档案解析出皮肤地址`() {
        val json =
            """{"id":"uuid","name":"Alex","skins":[{"id":"s","state":"ACTIVE","url":"https://textures.minecraft.net/texture/abc","variant":"CLASSIC"}]}"""

        val profile = MicrosoftJson.decodeFromString<MinecraftProfile>(json)

        assertEquals("https://textures.minecraft.net/texture/abc", profile.skins.single().url)
        assertEquals("ACTIVE", profile.skins.single().state)
    }

    @Test
    fun `XSTS 请求包含 SandboxId 与 RelyingParty`() {
        val body = MicrosoftJson.encodeToString(XstsRequest(properties = XstsProperties(userTokens = listOf("xbl"))))

        assertContains(body, "\"SandboxId\":\"RETAIL\"")
        assertContains(body, "\"RelyingParty\":\"rp://api.minecraftservices.com/\"")
        assertContains(body, "\"UserTokens\":[\"xbl\"]")
    }
}
