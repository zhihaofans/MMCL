package dev.mmcl.core.download

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** SHA-1 计算与校验。 */
object Sha1 {
    /** 计算文件 SHA-1，返回小写十六进制。 */
    fun of(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /** 校验文件 SHA-1 是否与期望一致（大小写不敏感）。 */
    fun matches(file: Path, expected: String): Boolean =
        Files.isRegularFile(file) && of(file).equals(expected, ignoreCase = true)
}
