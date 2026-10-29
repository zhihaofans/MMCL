package dev.mmcl.api.capability

import java.nio.file.Path

/**
 * 一次成功认证得到的账户信息。
 *
 * @property uuid 账户 UUID（离线账户为离线 UUID）
 * @property name 游戏内名称
 */
data class Account(
    val uuid: String,
    val name: String,
)

/**
 * 认证方式扩展点，由认证类插件注册到 [dev.mmcl.api.service.ServiceRegistry]。
 */
interface AuthProvider {
    /** 全局唯一标识，例如 `microsoft`、`offline`。 */
    val id: String

    /** 执行登录并返回账户；实现方必须自行处理刷新与持久化。 */
    suspend fun login(): Account
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
