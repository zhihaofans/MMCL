package dev.mmcl.auth.microsoft

/** 账户页的全部文案；中英各一份，按 `LocalLauncherLocale` 选。 */
internal data class MicrosoftTexts(
    val pageTitle: String,
    val subtitle: String,
    val clientIdTitle: String,
    val clientIdHint: String,
    val clientIdLabel: String,
    val clientIdSave: String,
    val clientIdSaved: String,
    val signedInAs: String,
    val signOut: String,
    val signIn: String,
    val waitingTitle: String,
    val waitingHint: String,
    val userCodeLabel: String,
    val openBrowser: String,
    val copyCode: String,
    val copied: String,
    val cancel: String,
    val exchanging: String,
    val retry: String,
    val failurePrefix: String,
    val notSignedIn: String,
)

internal val MICROSOFT_ENGLISH =
    MicrosoftTexts(
        pageTitle = "Accounts",
        subtitle = "Microsoft account for Minecraft: Java Edition",
        clientIdTitle = "Azure client ID",
        clientIdHint =
            "This launcher embeds no client ID. Register a public client app in Microsoft Entra " +
                "(enable public client flows and device code), ask Mojang for Minecraft API approval, " +
                "then paste the client ID here.",
        clientIdLabel = "Client ID",
        clientIdSave = "Save",
        clientIdSaved = "Saved. You can sign in now.",
        signedInAs = "Signed in as %s",
        signOut = "Sign out",
        signIn = "Sign in with Microsoft",
        waitingTitle = "Finish signing in",
        waitingHint = "Open the link below and enter this code. This page updates when you finish.",
        userCodeLabel = "Code",
        openBrowser = "Open browser",
        copyCode = "Copy code",
        copied = "Copied",
        cancel = "Cancel",
        exchanging = "Exchanging tokens with Minecraft…",
        retry = "Try again",
        failurePrefix = "Sign-in failed: %s",
        notSignedIn = "Not signed in",
    )

internal val MICROSOFT_CHINESE =
    MicrosoftTexts(
        pageTitle = "账户",
        subtitle = "用于 Minecraft Java 版的微软账号",
        clientIdTitle = "Azure client id",
        clientIdHint =
            "启动器不内置 client id。请先在 Microsoft Entra 注册一个公共客户端应用（打开「允许公共客户端流」与设备码流程），" +
                "向 Mojang 申请 Minecraft API 权限后，把 client id 填到这里。",
        clientIdLabel = "client id",
        clientIdSave = "保存",
        clientIdSaved = "已保存，现在可以登录。",
        signedInAs = "已登录：%s",
        signOut = "退出登录",
        signIn = "用微软账号登录",
        waitingTitle = "完成登录",
        waitingHint = "打开下面的链接并输入验证码；完成授权后本页会自动刷新。",
        userCodeLabel = "验证码",
        openBrowser = "打开浏览器",
        copyCode = "复制验证码",
        copied = "已复制",
        cancel = "取消",
        exchanging = "正在向 Minecraft 换取会话…",
        retry = "重试",
        failurePrefix = "登录失败：%s",
        notSignedIn = "未登录",
    )

/** 按语言标签选文案。 */
internal fun microsoftTexts(locale: String): MicrosoftTexts =
    if (locale.lowercase().startsWith("zh")) MICROSOFT_CHINESE else MICROSOFT_ENGLISH

/** 失败原因的可操作文案。 */
internal fun failureText(reason: MicrosoftAuthException.Reason, locale: String): String {
    val zh = locale.lowercase().startsWith("zh")
    return when (reason) {
        MicrosoftAuthException.Reason.NO_CLIENT_ID ->
            if (zh) "还没有填写 client id" else "No client ID configured"
        MicrosoftAuthException.Reason.DEVICE_CODE_EXPIRED ->
            if (zh) "验证码已过期，请重新登录" else "The code expired, please sign in again"
        MicrosoftAuthException.Reason.AUTHORIZATION_DECLINED ->
            if (zh) "你取消了授权" else "Authorization was declined"
        MicrosoftAuthException.Reason.OAUTH_ERROR ->
            if (zh) "微软授权失败" else "Microsoft authorization failed"
        MicrosoftAuthException.Reason.NO_XBOX_ACCOUNT ->
            if (zh) "该微软账号没有 Xbox 档案，请先登录 xbox.com 创建" else "This account has no Xbox profile"
        MicrosoftAuthException.Reason.COUNTRY_UNSUPPORTED ->
            if (zh) "所在国家/地区不支持 Xbox Live" else "Xbox Live is not available in your country"
        MicrosoftAuthException.Reason.ADULT_VERIFICATION_REQUIRED ->
            if (zh) "需要进行成人验证" else "Adult verification is required"
        MicrosoftAuthException.Reason.CHILD_ACCOUNT ->
            if (zh) "未成年人账号需要加入家庭组" else "Child accounts must join a family group"
        MicrosoftAuthException.Reason.NO_MINECRAFT_PROFILE ->
            if (zh) "该账号没有 Minecraft: Java 版档案" else "This account has no Minecraft: Java profile"
        MicrosoftAuthException.Reason.NO_GAME_ENTITLEMENT ->
            if (zh) "该账号没有 Java 版游戏权益（未购买）" else "This account does not own Java Edition"
        MicrosoftAuthException.Reason.ACCOUNT_MISMATCH ->
            if (zh) "账号与本地记录不一致，请重新登录" else "Account mismatch, please sign in again"
        MicrosoftAuthException.Reason.CLIENT_ID_NOT_APPROVED ->
            if (zh) "Azure 应用还没有 Minecraft API 权限（需向 Mojang 申请）"
            else "The Azure app is not approved for the Minecraft API yet"
        MicrosoftAuthException.Reason.CLIENT_ID_INVALID ->
            if (zh) "client id 无效，或 Azure 应用没开启「允许公共客户端流」"
            else "Invalid client ID, or public client flows are not enabled"
        MicrosoftAuthException.Reason.NOT_SIGNED_IN ->
            if (zh) "登录已失效，请重新登录" else "Your session expired, please sign in again"
        MicrosoftAuthException.Reason.INVALID_RESPONSE ->
            if (zh) "服务器返回了无法识别的数据" else "The server returned an unexpected response"
        MicrosoftAuthException.Reason.HTTP_ERROR ->
            if (zh) "网络请求失败，请检查网络后重试" else "Network request failed, please retry"
    }
}
