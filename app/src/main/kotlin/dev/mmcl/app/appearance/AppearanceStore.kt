package dev.mmcl.app.appearance

import dev.mmcl.api.service.SettingsService
import java.nio.file.Files
import java.nio.file.Path

/** 用户级外观偏好；路径为空表示跟随默认。 */
data class AppearancePreference(
    val fontFile: Path? = null,
    val backgroundImage: Path? = null,
)

/**
 * 用户级外观设置的读写。
 *
 * 只认**存在且是普通文件**的路径：用户把字体文件删了、U 盘拔了以后，
 * 下次启动应该安静地退回默认字体，而不是每次启动都报错。
 *
 * @param settings 键值设置；为 `null` 时永远返回默认值
 */
class AppearanceStore(private val settings: SettingsService?) {
    /** 读取外观偏好，顺手把失效路径过滤掉。 */
    fun load(): AppearancePreference =
        AppearancePreference(fontFile = fontFile(), backgroundImage = backgroundImage())

    /** 当前生效的字体文件；不存在或不可读时返回 `null`。 */
    fun fontFile(): Path? {
        val raw = settings?.getString(KEY_FONT_FILE)?.takeIf { it.isNotBlank() } ?: return null
        val path = runCatching { Path.of(raw) }.getOrNull() ?: return null
        return path.takeIf { isUsableFont(it) }
    }

    /** 设置字体文件；传 `null` 恢复默认。 */
    fun setFontFile(path: Path?) {
        settings?.putString(KEY_FONT_FILE, path?.toAbsolutePath()?.normalize()?.toString())
    }

    /** 当前生效的背景图；不存在或不可读时返回 `null`。 */
    fun backgroundImage(): Path? {
        val raw = settings?.getString(KEY_BACKGROUND_IMAGE)?.takeIf { it.isNotBlank() } ?: return null
        val path = runCatching { Path.of(raw) }.getOrNull() ?: return null
        return path.takeIf { isUsableImage(it) }
    }

    /** 设置背景图；传 `null` 恢复跟随主题。 */
    fun setBackgroundImage(path: Path?) {
        settings?.putString(KEY_BACKGROUND_IMAGE, path?.toAbsolutePath()?.normalize()?.toString())
    }

    companion object {
        /** 设置键：自定义字体文件路径。 */
        const val KEY_FONT_FILE: String = "ui.font.file"

        /** 设置键：自定义背景图路径。 */
        const val KEY_BACKGROUND_IMAGE: String = "ui.background.image"

        /** 允许的字体扩展名。 */
        val SUPPORTED_EXTENSIONS: Set<String> = setOf("ttf", "otf", "ttc")

        /** 允许的背景图扩展名。 */
        val SUPPORTED_IMAGE_EXTENSIONS: Set<String> = setOf("png", "jpg", "jpeg", "webp")

        /** 路径是否是一张可用的背景图。 */
        fun isUsableImage(path: Path): Boolean {
            if (!Files.isRegularFile(path)) return false
            val name = path.fileName?.toString()?.lowercase() ?: return false
            return SUPPORTED_IMAGE_EXTENSIONS.any { name.endsWith(".$it") }
        }

        /** 路径是否是一个可用的字体文件。 */
        fun isUsableFont(path: Path): Boolean {
            if (!Files.isRegularFile(path)) return false
            val name = path.fileName?.toString()?.lowercase() ?: return false
            return SUPPORTED_EXTENSIONS.any { name.endsWith(".$it") }
        }
    }
}
