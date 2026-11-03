package dev.mmcl.auth.microsoft

/** 微软登录过程中的可预期失败，UI 按 [reason] 本地化展示。 */
internal class MicrosoftAuthException(
    val reason: Reason,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: reason.name, cause) {
    /** 失败原因；前几个来自 OAuth，中间四个来自 XSTS 错误码，其余是本地状态问题。 */
    enum class Reason {
        /** 插件设置里没填 client id。 */
        NO_CLIENT_ID,

        /** 设备码过期，需要重新发起登录。 */
        DEVICE_CODE_EXPIRED,

        /** 用户在浏览器里点了拒绝。 */
        AUTHORIZATION_DECLINED,

        /** 其它 OAuth 错误。 */
        OAUTH_ERROR,

        /** XSTS `2148916233`：该微软账号没有 Xbox 档案。 */
        NO_XBOX_ACCOUNT,

        /** XSTS `2148916235`：所在国家/地区不支持 Xbox Live。 */
        COUNTRY_UNSUPPORTED,

        /** XSTS `2148916236` / `2148916237`：需要成人验证。 */
        ADULT_VERIFICATION_REQUIRED,

        /** XSTS `2148916238`：未成年人账号，需要加入家庭组。 */
        CHILD_ACCOUNT,

        /** 登录成功但该账号没有 Minecraft: Java 版档案。 */
        NO_MINECRAFT_PROFILE,

        /** 账号没有 Java 版游戏权益（没买过、Game Pass 也不在其中）。 */
        NO_GAME_ENTITLEMENT,

        /** 刷新后拿到的账号与本地保存的不一致，需要重新授权。 */
        ACCOUNT_MISMATCH,

        /** Azure 应用未获 Minecraft API 权限（`login_with_xbox` 返回 403）。 */
        CLIENT_ID_NOT_APPROVED,

        /** 当前没有可用令牌，需要重新登录。 */
        NOT_SIGNED_IN,

        /** 响应结构不符合预期。 */
        INVALID_RESPONSE,

        /** 网络层或 HTTP 非 2xx。 */
        HTTP_ERROR,
    }
}
