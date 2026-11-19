package dev.mmcl.auth.microsoft

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 一次 Minecraft 会话。
 *
 * @property uuid 32 位十六进制、无连字符（直接填进启动参数）
 * @property accessToken Minecraft 访问令牌，注入 `${auth_access_token}`
 */
internal data class MinecraftSession(
    val uuid: String,
    val name: String,
    val accessToken: String,
    val expiresAtEpochSeconds: Long,
)

/**
 * Xbox Live → XSTS → Minecraft Services 的登录链。
 *
 * 三次调用缺一不可：微软令牌只证明"是这个人"，XSTS 才带 Xbox 身份，
 * Minecraft 令牌才是游戏要的。任何一步失败都抛 [MicrosoftAuthException]。
 */
internal class XboxLiveClient(
    private val transport: HttpTransport,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    /** 用微软访问令牌换 Minecraft 会话。 */
    suspend fun exchange(microsoftAccessToken: String): MinecraftSession {
        val xbl = authenticateXboxLive(microsoftAccessToken)
        val xsts = authorizeXsts(xbl.token)
        val minecraft = loginWithXbox(xsts.uhs, xsts.token)
        ensureOwnsGame(minecraft.accessToken)
        val profile = fetchProfile(minecraft.accessToken)
        return MinecraftSession(
            uuid = profile.id,
            name = profile.name,
            accessToken = minecraft.accessToken,
            expiresAtEpochSeconds = now() + minecraft.expiresIn,
        )
    }

    private suspend fun authenticateXboxLive(microsoftAccessToken: String): XboxToken {
        val request =
            XblRequest(properties = XblProperties(rpsTicket = "d=$microsoftAccessToken"))
        val body = postJson(XBL_URL, MicrosoftJson.encodeToString(request))
        val response = decode<XboxTokenResponse>(body, "XBL")
        return XboxToken(token = response.token, uhs = uhs(response, "XBL"))
    }

    private suspend fun authorizeXsts(xblToken: String): XboxToken {
        val request = XstsRequest(properties = XstsProperties(userTokens = listOf(xblToken)))
        val body =
            try {
                // 这里必须直接调 transport：XSTS 的 401 响应体带 XErr，要用原始异常解析
                transport.postJson(XSTS_URL, MicrosoftJson.encodeToString(request))
            } catch (e: HttpStatusException) {
                throw xstsFailure(e)
            }
        val response = decode<XboxTokenResponse>(body, "XSTS")
        return XboxToken(token = response.token, uhs = uhs(response, "XSTS"))
    }

    private suspend fun loginWithXbox(uhs: String, xstsToken: String): MinecraftLoginResponse {
        val request = MinecraftLoginRequest(identityToken = "XBL3.0 x=$uhs;$xstsToken")
        val body =
            try {
                transport.postJson(MINECRAFT_LOGIN_URL, MicrosoftJson.encodeToString(request))
            } catch (e: HttpStatusException) {
                if (e.status == 403) {
                    // 新注册的 Azure 应用默认没有 Minecraft API 权限，需要向 Mojang 申请 AppID 审批
                    throw MicrosoftAuthException(
                        MicrosoftAuthException.Reason.CLIENT_ID_NOT_APPROVED,
                        "Azure 应用没有 Minecraft API 权限（403），需要向 Mojang 申请审批",
                        e,
                    )
                }
                throw MicrosoftAuthException(MicrosoftAuthException.Reason.HTTP_ERROR, e.message, e)
            }
        return decode<MinecraftLoginResponse>(body, "Minecraft 登录")
    }

    /**
     * 检查账号是否拥有 Java 版游戏权益。
     *
     * 只有 Xbox 账号不等于买过游戏：`entitlements/mcstore` 里没有
     * `game_minecraft` / `product_minecraft` 时直接失败，避免用户等到启动才看到
     * "没有正版授权"这种难懂的报错。
     */
    private suspend fun ensureOwnsGame(accessToken: String) {
        val body =
            try {
                transport.getJson(ENTITLEMENTS_URL, accessToken)
            } catch (e: HttpStatusException) {
                if (e.status == 401) {
                    throw MicrosoftAuthException(MicrosoftAuthException.Reason.NOT_SIGNED_IN, "Minecraft 令牌被拒绝", e)
                }
                throw MicrosoftAuthException(
                    MicrosoftAuthException.Reason.HTTP_ERROR,
                    "GET 权益 $ENTITLEMENTS_URL 失败：${e.message}",
                    e,
                )
            }
        val response = decode<EntitlementsResponse>(body, "游戏权益")
        val ownsGame =
            response.items.any { item ->
                item.name.equals("game_minecraft", ignoreCase = true) ||
                    item.name.equals("product_minecraft", ignoreCase = true)
            }
        if (!ownsGame) {
            throw MicrosoftAuthException(
                MicrosoftAuthException.Reason.NO_GAME_ENTITLEMENT,
                "该账号没有 Minecraft: Java 版游戏权益",
            )
        }
    }

    private suspend fun fetchProfile(accessToken: String): MinecraftProfile {
        val body =
            try {
                transport.getJson(PROFILE_URL, accessToken)
            } catch (e: HttpStatusException) {
                if (e.status == 404) {
                    throw MicrosoftAuthException(
                        MicrosoftAuthException.Reason.NO_MINECRAFT_PROFILE,
                        "该账号没有 Minecraft: Java 版档案",
                    )
                }
                throw MicrosoftAuthException(
                    MicrosoftAuthException.Reason.HTTP_ERROR,
                    "GET 档案 $PROFILE_URL 失败：${e.message}",
                    e,
                )
            }
        return decode<MinecraftProfile>(body, "档案")
    }

    private suspend fun postJson(url: String, body: String): String =
        try {
            transport.postJson(url, body)
        } catch (e: HttpStatusException) {
            throw MicrosoftAuthException(
                MicrosoftAuthException.Reason.HTTP_ERROR,
                "POST $url 失败：${e.message}",
                e,
            )
        }

    private fun uhs(response: XboxTokenResponse, step: String): String {
        val uhs = response.displayClaims?.xui?.firstOrNull()?.uhs
        return uhs ?: throw invalid("$step 响应缺少 uhs")
    }

    /** XSTS 的 401 响应体里带 `XErr`，把它翻译成可提示的原因。 */
    private fun xstsFailure(e: HttpStatusException): MicrosoftAuthException {
        val code =
            runCatching { MicrosoftJson.decodeFromString<XstsErrorResponse>(e.body).xErr }.getOrNull()
        val reason =
            when (code) {
                2148916233L -> MicrosoftAuthException.Reason.NO_XBOX_ACCOUNT
                2148916235L -> MicrosoftAuthException.Reason.COUNTRY_UNSUPPORTED
                2148916236L, 2148916237L -> MicrosoftAuthException.Reason.ADULT_VERIFICATION_REQUIRED
                2148916238L -> MicrosoftAuthException.Reason.CHILD_ACCOUNT
                else -> MicrosoftAuthException.Reason.HTTP_ERROR
            }
        return MicrosoftAuthException(reason, "XSTS 失败（XErr=$code）", e)
    }

    private inline fun <reified T> decode(body: String, step: String): T =
        try {
            MicrosoftJson.decodeFromString<T>(body)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw invalid("$step 响应结构不符合预期：${body.take(160)}", e)
        }

    private fun invalid(message: String, cause: Throwable? = null): MicrosoftAuthException =
        MicrosoftAuthException(MicrosoftAuthException.Reason.INVALID_RESPONSE, message, cause)

    private data class XboxToken(val token: String, val uhs: String)

    private companion object {
        const val XBL_URL = "https://user.auth.xboxlive.com/user/authenticate"
        const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
        const val MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        const val ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore"
        const val PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
    }
}
