package dev.mmcl.core.download

/**
 * 计划下载的一个文件。
 *
 * @property relativePath 相对游戏根目录的路径，统一用 `/` 分隔
 * @property url 下载地址，来自版本清单/详情数据（也就是插件提供的源）
 * @property sha1 期望的 SHA-1；为空时只校验大小
 * @property size 期望大小；为 0 时跳过大小校验
 * @property fallbackUrl 镜像地址失败时回退的原地址；`null` 表示没有回退
 */
data class PlannedFile(
    val relativePath: String,
    val url: String,
    val sha1: String = "",
    val size: Long = 0,
    val fallbackUrl: String? = null,
)
