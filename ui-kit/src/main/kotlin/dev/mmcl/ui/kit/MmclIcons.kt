package dev.mmcl.ui.kit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mmcl.api.ui.UiIcon

/**
 * [UiIcon] 到内置矢量图的唯一映射。
 *
 * 插件不能自带图标资源，只能从 [UiIcon] 里选，保证三平台渲染一致、插件 JAR 不带二进制资源。
 */
object MmclIcons {
    /** 把导航图标映射成矢量图。 */
    fun of(icon: UiIcon): ImageVector =
        when (icon) {
            UiIcon.BACK -> Icons.Filled.ArrowBack
            UiIcon.GAME -> Icons.Filled.PlayArrow
            UiIcon.DOWNLOAD -> Icons.Filled.KeyboardArrowDown
            UiIcon.SETTINGS -> Icons.Filled.Settings
            UiIcon.PLUGIN -> Icons.Filled.List
            UiIcon.ACCOUNT -> Icons.Filled.Person
            UiIcon.HOME -> Icons.Filled.Home
            UiIcon.WRENCH -> Icons.Filled.Build
            UiIcon.INFO -> Icons.Filled.Info
            UiIcon.EXTENSION -> Icons.Filled.Add
        }
}
