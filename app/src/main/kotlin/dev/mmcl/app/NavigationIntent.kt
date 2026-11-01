package dev.mmcl.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 页面之间的轻量跳转意图（app 内部，不改 `api-ui` 的 `AppNavigator`）。
 *
 * 目前只有一件事：从游戏页点「去下载」时，把要处理的版本带过去。
 * 下载页在进入时 [consumeDownloadVersion] 取走并清空，避免重复提示。
 */
class NavigationIntent {
    /** 待处理的版本 id；没有请求时为 `null`。 */
    var pendingDownloadVersion: String? by mutableStateOf(null)
        private set

    /** 请求下载页聚焦某个版本（例如缺文件的实例版本）。 */
    fun requestDownload(versionId: String) {
        pendingDownloadVersion = versionId
    }

    /** 取走并清空待处理版本。 */
    fun consumeDownloadVersion(): String? {
        val current = pendingDownloadVersion
        pendingDownloadVersion = null
        return current
    }

    /** 待启动的实例 id（首页点「启动」后由游戏页消费）。 */
    var pendingLaunchInstanceId: String? by mutableStateOf(null)
        private set

    /** 请求游戏页启动某个实例。 */
    fun requestLaunch(instanceId: String) {
        pendingLaunchInstanceId = instanceId
    }

    /** 取走并清空待启动实例。 */
    fun consumeLaunchInstance(): String? {
        val current = pendingLaunchInstanceId
        pendingLaunchInstanceId = null
        return current
    }
}
