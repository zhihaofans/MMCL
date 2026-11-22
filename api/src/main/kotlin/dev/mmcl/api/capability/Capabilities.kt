package dev.mmcl.api.capability


/**
 * 一次成功认证得到的账户信息。
 *
 * @property uuid 账户 UUID（32 位十六进制，无连字符；离线账户为离线 UUID）
 * @property name 游戏内名称
 * @property accessToken 启动时注入 `${auth_access_token}`；离线为 `0`
 * @property userType 启动时注入 `${user_type}`（`legacy` / `msa`）
 */
data class Account(
    val uuid: String,
    val name: String,
    val accessToken: String = "0",
    val userType: String = "legacy",
)

/**
 * 认证方式扩展点，由认证类插件注册到 [dev.mmcl.api.service.ServiceRegistry]。
 */
interface AuthProvider {
    /** 全局唯一标识，例如 `microsoft`、`offline`。 */
    val id: String

    /** 展示名，默认与 [id] 相同。 */
    val displayName: String get() = id

    /** 选择优先级，数值越小越优先。 */
    val priority: Int get() = 100

    /**
     * 当前登录的账户名，未登录或没有账户概念时为 `null`。
     *
     * 首页/游戏页优先显示它；为 `null` 时回退到设置里的离线玩家名（`player.name`）。
     * 默认 `null`，旧插件行为不变。
     */
    val accountName: String?
        get() = null

    /**
     * 当前账户的皮肤图片地址，供首页/顶栏的头像预览使用。
     *
     * 由认证插件提供（通常来自游戏档案里的 `skins[0].url`），启动器只负责下载与渲染；
     * `null` 表示没有可用皮肤，界面显示首字母占位头像。默认 `null`，旧插件行为不变。
     */
    val skinUrl: String?
        get() = null

    /**
     * 当前是否已有可用账户。
     *
     * 启动器只在已登录的认证方式里挑优先级最高的那个：装了正版登录插件但还没登录时，
     * 它不应该把离线账户挤掉。默认 `true`，旧插件行为不变。
     */
    val isSignedIn: Boolean get() = true

    /** 执行登录并返回账户；实现方必须自行处理刷新与持久化。 */
    suspend fun login(): Account

    /**
     * 为指定用户名创建一次启动用的账户。
     *
     * 默认直接走 [login]（正版登录不关心用户名）；离线实现覆盖它，用用户名生成离线 UUID。
     *
     * @param username 用户希望使用的游戏内名称
     */
    suspend fun createSession(username: String): Account = login()
}

/**
 * 内容检索结果。
 *
 * @property id 源站项目 id
 * @property title 项目标题
 * @property source 产出该结果的下载源 id
 */
data class ProjectHit(
    val id: String,
    val title: String,
    val source: String,
)

/**
 * 下载地址镜像扩展点：把官方地址改写成镜像地址。
 *
 * 由镜像插件（例如 `source-bmclapi`）注册；加载器/内容插件在生成下载清单前询问。
 * 多个实现按注册顺序询问，第一个返回非 `null` 的生效；返回 `null` 表示不处理。
 */
interface UrlMirror {
    /** 全局唯一标识，例如 `bmclapi`。 */
    val id: String

    /** 改写地址；不认识这个地址时返回 `null`。 */
    fun resolve(url: String): String?
}

/**
 * 内容下载源扩展点（Modrinth / CurseForge / 自定义源）。
 */
interface DownloadSource {
    /** 全局唯一标识，例如 `modrinth`。 */
    val id: String

    /** 按关键词检索项目。 */
    suspend fun search(query: String): List<ProjectHit>
}

/** 加载器的一个可用版本。 */
data class LoaderVersion(
    /** 加载器版本号，例如 `0.20.1`。 */
    val version: String,
    /** 是否稳定版；UI 默认选最新的稳定版。 */
    val stable: Boolean = true,
    /** 可选的补充说明（例如 beta 提示）。 */
    val note: String? = null,
)

/**
 * 安装加载器的请求。
 *
 * @param gameVersionId 目标 Minecraft 版本（例如 `26.3`）
 * @param loaderVersion 指定加载器版本；`null` 表示由插件选推荐版本
 */
data class LoaderInstallRequest(
    val gameVersionId: String,
    val loaderVersion: String? = null,
)

/**
 * 加载器安装结果。
 *
 * 插件**只生成版本 JSON**（含 `inheritsFrom`，指向基础版本），写盘、目录结构、
 * 下载与继承合并全部由启动器负责。
 *
 * @param versionId 新版本 id，例如 `fabric-loader-0.20.1-26.3`
 * @param detailJson 版本 JSON 原文，启动器写到 `versions/<id>/<id>.json`
 * @param warnings 非致命问题（例如某个库没有哈希）
 */
data class LoaderInstallResult(
    val versionId: String,
    val detailJson: String,
    val warnings: List<String> = emptyList(),
)

/**
 * 游戏加载器安装扩展点（Forge / Fabric / NeoForge / Quilt）。
 */
interface LoaderInstaller {
    /** 加载器标识，例如 `fabric`。 */
    val loaderId: String

    /** 查询该加载器对目标游戏版本可用的版本（最新在前）。 */
    suspend fun listVersions(gameVersionId: String): List<LoaderVersion>

    /**
     * 生成加载器版本。
     *
     * @throws Exception 元数据不可用、目标版本不支持或 loader 版本非法时
     */
    suspend fun install(request: LoaderInstallRequest): LoaderInstallResult
}
