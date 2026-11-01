package dev.mmcl.core.instance

import kotlinx.serialization.Serializable

/**
 * 一个游戏实例：绑定某个版本，拥有独立的游戏目录（存档、配置、mods）。
 *
 * libraries / assets / versions 由所有实例共享（`<dataDir>` 根目录），
 * 因此实例目录里只放"玩家自己的东西"。
 *
 * @property id 目录名，文件系统安全
 * @property name 展示名，默认与版本号一致，重名时自动加后缀
 * @property versionId 绑定的 Minecraft 版本
 * @property lastLaunchedAtEpochMillis 最近一次成功启动的时间，0 表示从未启动
 */
@Serializable
data class Instance(
    val id: String,
    val name: String,
    val versionId: String,
    val createdAtEpochMillis: Long = 0,
    val lastLaunchedAtEpochMillis: Long = 0,
)
