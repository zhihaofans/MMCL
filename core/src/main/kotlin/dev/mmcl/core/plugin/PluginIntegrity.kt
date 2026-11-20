package dev.mmcl.core.plugin

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarFile

/** 插件 JAR 的签名状态。 */
sealed interface PluginSignature {
    /** 没有签名（当前官方插件都是这种）。 */
    data object Unsigned : PluginSignature

    /** 签名有效；[signers] 是签名证书的 subject DN。 */
    data class Signed(val signers: List<String>) : PluginSignature

    /** 签名存在但校验失败（内容被篡改或证书损坏）。 */
    data class Invalid(val reason: String) : PluginSignature
}

/**
 * 插件 JAR 的完整性与签名信息，加载时算一次，供插件页展示。
 *
 * @property sha256 整个 JAR 的 SHA-256（小写十六进制）
 * @property signature 签名状态
 */
data class PluginIntegrity(
    val sha256: String,
    val signature: PluginSignature,
) {
    /** 前 12 位摘要，给界面用。 */
    val shortSha256: String
        get() = sha256.take(12)
}

/** 插件被忽略的原因。 */
enum class PluginConflictReason {
    /** 同一 id 的重复 JAR（多版本，或同版本不同内容）。 */
    DUPLICATE,

    /** JAR 签名校验失败，拒绝加载。 */
    INVALID_SIGNATURE,

    /** JAR 本身损坏/无法读取。 */
    LOAD_FAILED,
}

/**
 * 被加载器忽略的插件 JAR。
 *
 * @property jar 出问题的 JAR
 * @property id 能识别出的插件 id；JAR 损坏时可能为 `null`
 * @property version 能识别出的版本；可能为 `null`
 * @property reason 忽略原因
 * @property detail 给界面看的补充说明（例如「被 0.0.3 取代」）
 */
data class PluginConflict(
    val jar: Path,
    val id: String? = null,
    val version: String? = null,
    val reason: PluginConflictReason,
    val detail: String? = null,
)

/** 计算完整性信息；签名校验会读取全部条目，损坏的签名会得到 [PluginSignature.Invalid]。 */
internal object PluginIntegrityVerifier {
    fun inspect(jar: Path): PluginIntegrity =
        PluginIntegrity(sha256 = sha256Of(jar), signature = signatureOf(jar))

    /** 流式计算 SHA-256，避免把整个 JAR 读进内存。 */
    fun sha256Of(jar: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(jar).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 用 `JarFile(..., verify = true)` 逐条读取触发签名校验。
     *
     * 未签名的 JAR 读到的是 `null` code signers；签名损坏时 JDK 会在读取时抛
     * `SecurityException`，这里转成 [PluginSignature.Invalid]，由加载器拒绝。
     */
    fun signatureOf(jar: Path): PluginSignature =
        runCatching {
            JarFile(jar.toFile(), true).use { file ->
                val signers = linkedSetOf<String>()
                val buffer = ByteArray(8 * 1024)
                val entries = file.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    file.getInputStream(entry).use { input ->
                        while (input.read(buffer) >= 0) {
                            // 读取即触发校验，内容本身不需要
                        }
                    }
                    entry.codeSigners?.forEach { signer ->
                        signer.signerCertPath?.certificates?.firstOrNull()
                            ?.let { it as? X509Certificate }
                            ?.subjectX500Principal
                            ?.name
                            ?.let(signers::add)
                    }
                }
                if (signers.isEmpty()) PluginSignature.Unsigned else PluginSignature.Signed(signers.toList())
            }
        }.getOrElse { PluginSignature.Invalid(it.message ?: it.javaClass.simpleName) }
}
