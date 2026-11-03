package dev.mmcl.auth.microsoft

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import org.slf4j.Logger

/** 落盘的登录态；`accessToken` 是 Minecraft 令牌，[accessTokenExpiresAt] 为 epoch 秒。 */
internal data class StoredTokens(
    val refreshToken: String,
    val accessToken: String?,
    val accessTokenExpiresAt: Long,
    val uuid: String?,
    val name: String?,
)

/**
 * 登录态持久化：`<dataDir>/plugin-data/dev.mmcl.auth.microsoft/tokens.properties`。
 *
 * 写入走「临时文件 + 原子替换」，并把权限收到 `rw-------`：
 * 刷新令牌等同于账号密码，绝不能出现在日志里（`ARCHITECTURE.md` §12.4）。
 *
 * @param file 令牌文件路径
 * @param logger 仅在权限收紧失败时告警
 */
internal class TokenStore(
    private val file: Path,
    private val logger: Logger,
) {
    /** 读取登录态；文件不存在或内容损坏时返回 `null`。 */
    fun load(): StoredTokens? {
        if (!Files.isRegularFile(file)) return null
        return runCatching {
            val properties = Properties()
            Files.newBufferedReader(file, Charsets.UTF_8).use(properties::load)
            val refresh = properties.getProperty(KEY_REFRESH_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
            StoredTokens(
                refreshToken = refresh,
                accessToken = properties.getProperty(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() },
                accessTokenExpiresAt = properties.getProperty(KEY_EXPIRES_AT)?.toLongOrNull() ?: 0,
                uuid = properties.getProperty(KEY_UUID)?.takeIf { it.isNotBlank() },
                name = properties.getProperty(KEY_NAME)?.takeIf { it.isNotBlank() },
            )
        }.onFailure { logger.warn("读取登录态失败，按未登录处理：{}", it.message) }
            .getOrNull()
    }

    /** 写入登录态；失败直接抛出，让上层决定是否提示用户。 */
    fun save(tokens: StoredTokens) {
        Files.createDirectories(file.parent)
        val properties =
            Properties().apply {
                setProperty(KEY_REFRESH_TOKEN, tokens.refreshToken)
                tokens.accessToken?.let { setProperty(KEY_ACCESS_TOKEN, it) }
                setProperty(KEY_EXPIRES_AT, tokens.accessTokenExpiresAt.toString())
                tokens.uuid?.let { setProperty(KEY_UUID, it) }
                tokens.name?.let { setProperty(KEY_NAME, it) }
            }
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.newBufferedWriter(temp, Charsets.UTF_8).use { writer -> properties.store(writer, "MMCL microsoft auth") }
        restrictToOwner(temp)
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
        restrictToOwner(file)
    }

    /** 删除登录态；文件本来就不存在时无副作用（`onDisable` 幂等要求）。 */
    fun clear() {
        runCatching { Files.deleteIfExists(file) }
            .onFailure { logger.warn("删除登录态失败：{}", it.message) }
    }

    private fun restrictToOwner(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }.onFailure {
            // Windows 上不支持 POSIX 权限；这里不阻断登录，只提示一次
            logger.info("无法收紧文件权限（{}），已跳过：{}", path.fileName, it.message)
        }
    }

    private companion object {
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_UUID = "uuid"
        const val KEY_NAME = "name"
    }
}
