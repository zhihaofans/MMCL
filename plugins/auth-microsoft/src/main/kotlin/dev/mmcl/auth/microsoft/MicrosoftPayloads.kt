package dev.mmcl.auth.microsoft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Microsoft 设备码响应（`/devicecode`）。 */
@Serializable
internal data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Long = 900,
    @SerialName("interval") val interval: Long = 5,
)

/** 令牌端点响应；失败时只有 `error` / `error_description`。 */
@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

/** Xbox Live `user/authenticate` 请求体。 */
@Serializable
internal data class XblRequest(
    @SerialName("Properties") val properties: XblProperties,
    @SerialName("RelyingParty") val relyingParty: String = "http://auth.xboxlive.com",
    @SerialName("TokenType") val tokenType: String = "JWT",
)

/** XBL 请求里的属性块。 */
@Serializable
internal data class XblProperties(
    @SerialName("AuthMethod") val authMethod: String = "RPS",
    @SerialName("SiteName") val siteName: String = "user.auth.xboxlive.com",
    @SerialName("RpsTicket") val rpsTicket: String,
)

/** XSTS `xsts/authorize` 请求体。 */
@Serializable
internal data class XstsRequest(
    @SerialName("Properties") val properties: XstsProperties,
    @SerialName("RelyingParty") val relyingParty: String = "rp://api.minecraftservices.com/",
    @SerialName("TokenType") val tokenType: String = "JWT",
)

/** XSTS 请求里的属性块。 */
@Serializable
internal data class XstsProperties(
    @SerialName("SandboxId") val sandboxId: String = "RETAIL",
    @SerialName("UserTokens") val userTokens: List<String>,
)

/** XBL / XSTS 共用的响应体。 */
@Serializable
internal data class XboxTokenResponse(
    @SerialName("Token") val token: String,
    @SerialName("DisplayClaims") val displayClaims: DisplayClaims? = null,
)

/** XBL / XSTS 响应里的身份声明。 */
@Serializable
internal data class DisplayClaims(
    @SerialName("xui") val xui: List<Xui> = emptyList(),
)

/** 用户哈希（`uhs`），拼 `XBL3.0 x=<uhs>;<token>` 用。 */
@Serializable
internal data class Xui(
    @SerialName("uhs") val uhs: String,
)

/** XSTS 401 响应体，`XErr` 是错误码。 */
@Serializable
internal data class XstsErrorResponse(
    @SerialName("XErr") val xErr: Long? = null,
    @SerialName("Message") val message: String? = null,
)

/** Minecraft `login_with_xbox` 请求体。 */
@Serializable
internal data class MinecraftLoginRequest(
    @SerialName("identityToken") val identityToken: String,
)

/** Minecraft `login_with_xbox` 响应体。 */
@Serializable
internal data class MinecraftLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 86_400,
)

/** Minecraft 权益响应体（`entitlements/mcstore`）。 */
@Serializable
internal data class EntitlementsResponse(
    val items: List<Entitlement> = emptyList(),
)

/** 一项游戏权益；Java 版有 `game_minecraft` / `product_minecraft`。 */
@Serializable
internal data class Entitlement(
    val name: String,
)

/** Minecraft 档案响应体。 */
@Serializable
internal data class MinecraftProfile(
    val id: String,
    val name: String,
)

/** 插件内部统一的 JSON 配置：微软的响应字段远多于我们需要的。 */
internal val MicrosoftJson: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    // 请求体的 RelyingParty / TokenType / AuthMethod / SiteName / SandboxId 都靠默认值，
    // 不序列化默认值的话 XBL/XSTS 会收到缺字段的 JSON 并回 400（响应体还是空的）
    encodeDefaults = true
    coerceInputValues = true
    explicitNulls = false
}
