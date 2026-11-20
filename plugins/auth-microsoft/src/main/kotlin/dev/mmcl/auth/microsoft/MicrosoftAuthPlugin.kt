package dev.mmcl.auth.microsoft

import dev.mmcl.api.ApiLevel
import dev.mmcl.api.capability.AuthProvider
import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext
import dev.mmcl.api.plugin.PluginIcon
import dev.mmcl.api.settings.SettingsSection
import dev.mmcl.api.ui.UiPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 微软账号登录插件。
 *
 * 只注册 [AuthProvider]；交互式设备码流程由同插件的账户页驱动
 * （页面直接持有本插件内部的 provider 实例，不走跨插件引用）。
 *
 * client id 来自设置项 [CLIENT_ID_KEY]：Azure 应用 ID 属于部署者自己的资源，
 * 不内置在仓库里（见 `PLUGIN_DEV.md`）。
 */
class MicrosoftAuthPlugin : Plugin {
    override val id: String = "dev.mmcl.auth.microsoft"
    override val version: String = "0.0.2"
    override val apiLevel: Int = ApiLevel.CURRENT
    override val title: String
        get() = if (isChinese(localeProvider())) "微软账号" else "Microsoft account"
    override val author: String = "MMCL"
    override val icon: PluginIcon = PluginIcon.AUTH

    /** `onLoad` 之前不读语言，先按英文兜底。 */
    private var localeProvider: () -> String = { ENGLISH }

    internal var provider: MicrosoftAuthProvider? = null
        private set

    internal var oauthClient: (() -> MicrosoftOAuthClient?)? = null
        private set

    /** 登录任务的作用域：挂在插件上，切页面不会取消登录。 */
    private var scope: CoroutineScope? = null

    override fun onLoad(context: PluginContext) {
        localeProvider = { context.locale }
        val transport = JdkHttpTransport()
        val tokenStore = TokenStore(context.dataDir.resolve(TOKEN_FILE), context.logger)
        val xbox = XboxLiveClient(transport)
        val factory: () -> MicrosoftOAuthClient? = {
            clientId(context)?.let { clientId -> MicrosoftOAuthClient(clientId, transport) }
        }
        val authProvider =
            MicrosoftAuthProvider(
                tokenStore = tokenStore,
                oauth = factory,
                xbox = xbox,
                logger = context.logger,
                localeProvider = { context.locale },
            )
        provider = authProvider
        oauthClient = factory
        context.services.register(AuthProvider::class, authProvider)

        val loginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = loginScope
        val loginService = MicrosoftLoginService(authProvider, factory, loginScope, context.logger)
        context.services.register(UiPage::class, AccountsPage(loginService, authProvider, { clientId(context) }))
        context.services.register(SettingsSection::class, MicrosoftAuthSettings())

        // 旧登录态没有皮肤地址：后台补一次，失败不影响启动
        if (authProvider.isSignedIn && authProvider.skinUrl == null) {
            loginScope.launch {
                runCatching { authProvider.backfillSkinUrl() }
                    .onFailure { context.logger.debug("皮肤地址补齐失败：{}", it.message) }
            }
        }

        if (clientId(context) == null) {
            context.logger.warn("未配置 {}，微软登录不可用（账户页会给注册指引）", CLIENT_ID_KEY)
        } else {
            context.logger.info("微软账号插件已加载（已登录：{}）", authProvider.isSignedIn)
        }
    }

    /**
     * client id 解析：设置项优先，其次 JVM 系统属性 [SYSTEM_PROPERTY]（打包/CI 用，免重新构建）。
     *
     * 两者都没有时返回 `null`：不内置任何他人 ID，未配置就不提供微软登录。
     */
    internal fun clientId(context: PluginContext): String? =
        context.settings.getString(CLIENT_ID_KEY)?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getProperty(SYSTEM_PROPERTY)?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        /** 设置键：Azure 应用（public client）的 client id。 */
        const val CLIENT_ID_KEY: String = "auth.microsoft.clientId"

        /** JVM 系统属性名：`-Dmmcl.microsoft.clientId=...`，优先级低于设置项。 */
        const val SYSTEM_PROPERTY: String = "mmcl.microsoft.clientId"

        /** 令牌文件名（相对插件数据目录）。 */
        internal const val TOKEN_FILE: String = "tokens.properties"
    }
}

/** 缺省语言：`onLoad` 之前用英文兜底。 */
private const val ENGLISH = "en"

/** 界面语言是否为中文；插件展示名跟随 `PluginContext.locale`，不写死。 */
private fun isChinese(locale: String): Boolean = locale.lowercase().startsWith("zh")
