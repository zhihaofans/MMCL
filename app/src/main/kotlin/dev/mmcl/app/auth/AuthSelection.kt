package dev.mmcl.app.auth

import dev.mmcl.api.capability.AuthProvider

/** 「自动选择账户」的设置值。 */
const val AUTO_PROVIDER: String = "auto"

/**
 * 选一个用于启动/展示的认证方式。
 *
 * - [preferredId] 指定的账户**已登录**时优先用它（用户在「设置 → 账户」里手动切换）
 * - 否则回退到原来的规则：在已登录的认证方式里按 `priority`、`id` 取最小
 *
 * 装了正版登录插件但还没登录时，它不应该顶掉离线账户，
 * 否则首页会显示"未登录"、启动直接失败（见 `AuthProvider.isSignedIn`）。
 */
fun List<AuthProvider>.activeAccount(preferredId: String? = null): AuthProvider? {
    val preferred = preferredId?.takeIf { it.isNotBlank() && it != AUTO_PROVIDER }
    if (preferred != null) {
        firstOrNull { it.id == preferred && it.isSignedIn }?.let { return it }
    }
    return filter { it.isSignedIn }.minWithOrNull(compareBy({ it.priority }, { it.id }))
}
