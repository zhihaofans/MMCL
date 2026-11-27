package dev.mmcl.core.content

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger

/**
 * 一个已安装 Mod。
 *
 * @property fileName 磁盘文件名（启用的以 `.jar` 结尾，禁用的以 `.jar.disabled` 结尾）
 * @property id `fabric.mod.json` 里的 id；无法读取时退回文件名
 * @property conflict 同 id 存在多个文件
 */
data class InstalledMod(
    val fileName: String,
    val id: String,
    val name: String,
    val version: String?,
    val bytes: Long,
    val enabled: Boolean,
    val conflict: Boolean = false,
)

/**
 * 实例 `mods/` 的扫描、启用/停用与删除。
 *
 * 启停约定：Fabric Loader 只加载 `.jar`；禁用时改名为 `.jar.disabled`（不删除文件）。
 * 只操作实例目录下的直接子文件，拒绝路径穿越与符号链接。
 */
class ModManager(
    private val logger: Logger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 列出实例已安装的 Mod（启用 + 禁用），按文件名排序。 */
    fun list(instanceDir: Path): List<InstalledMod> {
        val modsDir = instanceDir.resolve("mods")
        if (!Files.isDirectory(modsDir)) return emptyList()
        val items = mutableListOf<InstalledMod>()
        try {
            Files.list(modsDir).use { stream ->
                for (file in stream) {
                    if (!Files.isRegularFile(file)) continue
                    val fileName = file.fileName.toString()
                    val enabled =
                        when {
                            fileName.endsWith(".jar", ignoreCase = true) -> true
                            fileName.endsWith(".jar.disabled", ignoreCase = true) -> false
                            else -> continue
                        }
                    val metadata = readMetadata(file)
                    items +=
                        InstalledMod(
                            fileName = fileName,
                            id = metadata?.id ?: fileName.removeSuffix(".disabled").removeSuffix(".jar"),
                            name = metadata?.name ?: fileName,
                            version = metadata?.version,
                            bytes = runCatching { Files.size(file) }.getOrDefault(0),
                            enabled = enabled,
                        )
                }
            }
        } catch (e: IOException) {
            logger.warn("读取 mods 目录失败：{}", e.message)
        }
        val duplicates =
            items.groupBy { it.id }.filterValues { it.size > 1 }.keys
        return items
            .map { if (it.id in duplicates) it.copy(conflict = true) else it }
            .sortedWith(compareByDescending<InstalledMod> { it.enabled }.thenBy { it.fileName })
    }

    /** 启用/禁用：重命名 `.jar` ↔ `.jar.disabled`。 */
    fun setEnabled(instanceDir: Path, fileName: String, enabled: Boolean): Result<Path> =
        runCatching {
            val modsDir = instanceDir.resolve("mods")
            val source = safeChild(modsDir, fileName)
            if (!Files.isRegularFile(source)) throw IOException("文件不存在：$fileName")
            val targetName =
                if (enabled) {
                    if (!fileName.endsWith(".jar.disabled", ignoreCase = true)) throw IOException("已是启用状态：$fileName")
                    fileName.removeSuffix(".disabled")
                } else {
                    if (!fileName.endsWith(".jar", ignoreCase = true)) throw IOException("已是禁用状态：$fileName")
                    "$fileName.disabled"
                }
            val target = safeChild(modsDir, targetName)
            if (Files.exists(target)) throw IOException("目标文件已存在：$targetName")
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
            logger.info("Mod {}：{} -> {}", if (enabled) "已启用" else "已禁用", fileName, targetName)
            target
        }

    /** 删除一个 Mod 文件。 */
    fun delete(instanceDir: Path, fileName: String): Result<Unit> =
        runCatching {
            val source = safeChild(instanceDir.resolve("mods"), fileName)
            if (!Files.isRegularFile(source)) throw IOException("文件不存在：$fileName")
            Files.delete(source)
            logger.info("已删除 Mod：{}", fileName)
        }

    private data class Metadata(
        val id: String,
        val name: String,
        val version: String?,
    )

    private fun readMetadata(file: Path): Metadata? =
        runCatching {
            ZipFile(file.toFile()).use { zip ->
                val entry = zip.getEntry("fabric.mod.json") ?: return null
                val root = json.parseToJsonElement(zip.getInputStream(entry).bufferedReader().readText()).jsonObject
                Metadata(
                    id = root.string("id") ?: return null,
                    name = root.string("name") ?: root.string("id") ?: return null,
                    version = root.string("version"),
                )
            }
        }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.takeIf { it.isNotBlank() }

    private fun safeChild(parent: Path, name: String): Path {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
            throw IOException("非法文件名：$name")
        }
        val target = parent.resolve(name).normalize()
        if (!target.startsWith(parent.normalize())) throw IOException("非法路径：$name")
        if (Files.isSymbolicLink(target)) throw IOException("拒绝符号链接：$name")
        return target
    }
}
