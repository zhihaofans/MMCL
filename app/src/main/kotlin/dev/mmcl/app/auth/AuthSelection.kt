package dev.mmcl.app.auth

import dev.mmcl.api.capability.AuthProvider

/**
 * 在**已登录**的认证方式里按 `priority`、`id` 选一个。
 *
 * 装了正版登录插件但还没登录时，它不应该顶掉离线账户，
 * 否则首页会显示"未登录"、启动直接失败（见 `AuthProvider.isSignedIn`）。
 */
fun List<AuthProvider>.activeAccount(): AuthProvider? =
    filter { it.isSignedIn }.minWithOrNull(compareBy({ it.priority }, { it.id }))
