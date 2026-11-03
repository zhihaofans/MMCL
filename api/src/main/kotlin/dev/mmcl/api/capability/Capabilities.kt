package dev.mmcl.api.capability

import java.nio.file.Path

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
 * 内容下载源扩展点（Modrinth / CurseForge / 自定义源）。
 */
interface DownloadSource {
    /** 全局唯一标识，例如 `modrinth`。 */
    val id: String

    /** 按关键词检索项目。 */
    suspend fun search(query: String): List<ProjectHit>
}

/**
 * 游戏加载器安装扩展点（Forge / Fabric / NeoForge / Quilt）。
 */
interface LoaderInstaller {
    /** 加载器标识，例如 `fabric`。 */
    val loaderId: String

    /**
     * 安装加载器。
     *
     * @param minecraftVersion 目标 Minecraft 版本
     * @param targetDir 实例目录
     */
    suspend fun install(minecraftVersion: String, targetDir: Path)
}
