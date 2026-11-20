package dev.mmcl.app.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import dev.mmcl.ui.kit.SkinHead
import kotlinx.coroutines.delay

/** 旧登录态可能在后台补皮肤地址：没拿到时最多再试几次。 */
private const val POLL_ATTEMPTS = 4

/** 两次重试的间隔。 */
private const val POLL_DELAY_MILLIS = 1_500L

/**
 * 带加载逻辑的皮肤头像：地址变化/后台补齐后自动加载，加载完成前显示占位头像。
 *
 * @param loader 皮肤加载器；测试或安全模式下可为 `null`
 * @param urlProvider 每次调用都返回最新皮肤地址（认证插件的 getter 直接读本地登录态）
 * @param name 玩家名（占位头像首字母）
 * @param size 头像边长
 */
@Composable
fun SkinAvatar(
    loader: SkinLoader?,
    urlProvider: () -> String?,
    name: String,
    size: Dp,
) {
    val latestProvider by rememberUpdatedState(urlProvider)
    // 组合时看一眼当前地址：切账号/补齐完成后重新触发
    val currentUrl = urlProvider()
    var image by remember(name, currentUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(loader, name, currentUrl) {
        repeat(POLL_ATTEMPTS) { attempt ->
            val url = latestProvider()
            if (!url.isNullOrBlank()) {
                image = loader?.load(url)
                if (image != null) return@LaunchedEffect
            }
            if (attempt < POLL_ATTEMPTS - 1) delay(POLL_DELAY_MILLIS)
        }
    }

    SkinHead(image = image, name = name, size = size)
}
