package dev.mmcl.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** 64×64 皮肤图里「脸」的位置：从 (8,8) 开始的 8×8 像素。 */
private const val FACE_OFFSET = 8

/** 脸的边长（像素）。 */
private const val FACE_SIZE = 8

/**
 * 像素风皮肤头像：从皮肤 PNG 里裁出脸部并放大显示。
 *
 * 皮肤图来自认证插件提供的档案数据；[image] 为 `null`（离线账户或加载失败）时
 * 显示首字母占位头像，不阻塞布局。
 *
 * @param image 皮肤图（64×64，旧版 64×32 也兼容）
 * @param name 玩家名，用于占位头像的首字母与无障碍描述
 * @param size 头像边长
 */
@Composable
fun SkinHead(
    image: ImageBitmap?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.25f)
    if (image != null && image.width >= FACE_OFFSET + FACE_SIZE && image.height >= FACE_OFFSET + FACE_SIZE) {
        Image(
            painter =
                BitmapPainter(
                    image = image,
                    srcOffset = IntOffset(FACE_OFFSET, FACE_OFFSET),
                    srcSize = IntSize(FACE_SIZE, FACE_SIZE),
                    filterQuality = FilterQuality.None,
                ),
            contentDescription = name,
            modifier = modifier.size(size).clip(shape),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier =
                modifier.size(size)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
