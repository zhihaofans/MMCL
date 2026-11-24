package dev.mmcl.core.storage

import dev.mmcl.core.instance.Instance
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger

/** 存储分类。 */
enum class StorageCategoryId {
    INSTANCES,
    VERSIONS,
    LIBRARIES,
    ASSETS,
    RUNTIME,
    CACHE,
    LOGS,
    PLUGINS,
    OTHER,
}

/** 一个分类的占用。 */
data class StorageCategory(
    val id: StorageCategoryId,
    val bytes: Long,
)

/** 一个实例的占用。 */
data class StorageInstance(
    val id: String,
    val name: String,
    val versionId: String,
    val bytes: Long,
)

/** 版本删除被拒绝的原因。 */
enum class VersionBlock {
    USED_BY_INSTANCE,
    INHERITED_BY,
    RUNNING,
}

/** 一个已安装版本（游戏或加载器）的存储信息。 */
data class StorageVersion(
    val id: String,
    val bytes: Long,
    /** 加载器版本的父版本；游戏版本为 `null`。 */
    val baseVersionId: String?,
    val usedByInstanceIds: List<String>,
    val inheritedBy: List<String>,
    /** `null` 表示可删。 */
    val blocked: VersionBlock? = null,
) {
    val deletable: Boolean get() = blocked == null
}

/** 一个运行时目录。 */
data class StorageRuntime(
    val relativePath: String,
    val bytes: Long,
    val major: Int?,
    val protected: Boolean,
)

/** 一次扫描结果。 */
data class StorageSnapshot(
    val totalBytes: Long,
    val categories: List<StorageCategory>,
    val instances: List<StorageInstance>,
    val gameVersions: List<StorageVersion>,
    val loaderVersions: List<StorageVersion>,
    val runtimes: List<StorageRuntime>,
    val warnings: List<String> = emptyList(),
)

/** 删除被拒绝的原因。 */
enum class DeleteBlock {
    USED_BY_INSTANCE,
    INHERITED_BY,
    RUNNING,
    PROTECTED,
    INVALID_TARGET,
    NOT_FOUND,
}

/** 删除结果。 */
sealed interface DeleteOutcome {
    /** 删除成功，返回释放的字节数。 */
    data class Deleted(val bytes: Long) : DeleteOutcome

    /** 因安全规则拒绝。 */
    data class Blocked(val reason: DeleteBlock, val detail: String? = null) : DeleteOutcome

    /** IO 失败。 */
    data class Failed(val message: String) : DeleteOutcome
}

/**
 * 存储空间扫描与安全删除。
 *
 * 删除范围仅限 `<dataDir>` 下的实例、版本目录与运行时；不碰插件、存档之外的用户文件。
 * 版本删除前会重新读取实例列表与版本继承关系，避免删掉正在使用或被加载器继承的版本。
 *
 * @param dataDir 启动器数据目录（`<dataDir>/versions`、`instances` 等都在其下）
 * @param instancesProvider 当前实例列表，用于判断引用关系
 */
class StorageService(
    private val dataDir: Path,
    private val logger: Logger,
    private val instancesProvider: () -> List<Instance> = { emptyList() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val instancesDir: Path = dataDir.resolve("instances")
    private val versionsDir: Path = dataDir.resolve("versions")
    private val runtimeDir: Path = dataDir.resolve("runtime")

    /**
     * 扫描存储占用。
     *
     * @param protectedRuntimes 不允许删除的运行时目录（通常是当前策略正在使用的）
     * @param runningVersionIds 正在运行游戏的版本 id
     */
    fun scan(
        protectedRuntimes: Set<Path> = emptySet(),
        runningVersionIds: Set<String> = emptySet(),
    ): StorageSnapshot {
        val warnings = mutableListOf<String>()
        val instances = instancesProvider()

        val instanceItems =
            instances.map { instance ->
                StorageInstance(
                    id = instance.id,
                    name = instance.name,
                    versionId = instance.versionId,
                    bytes = sizeOf(instancesDir.resolve(instance.id), warnings),
                )
            }

        val metas = readVersionMetas(warnings)
        val usedBy = instances.groupBy { it.versionId }
        val inheritedBy = metas.values.filter { it.baseVersionId != null }.groupBy { it.baseVersionId!! }

        val versionItems =
            metas.values.sortedBy { it.id }.map { meta ->
                val children = inheritedBy[meta.id].orEmpty().map { it.id }.sorted()
                val users = usedBy[meta.id].orEmpty().map { it.id }.sorted()
                val blocked =
                    when {
                        meta.id in runningVersionIds -> VersionBlock.RUNNING
                        users.isNotEmpty() -> VersionBlock.USED_BY_INSTANCE
                        children.isNotEmpty() -> VersionBlock.INHERITED_BY
                        else -> null
                    }
                StorageVersion(
                    id = meta.id,
                    bytes = sizeOf(versionsDir.resolve(meta.id), warnings),
                    baseVersionId = meta.baseVersionId,
                    usedByInstanceIds = users,
                    inheritedBy = children,
                    blocked = blocked,
                )
            }

        val runtimes =
            listRuntimes(warnings).map { relative ->
                val path = runtimeDir.resolve(relative)
                StorageRuntime(
                    relativePath = relative.toString(),
                    bytes = sizeOf(path, warnings),
                    major = MAJOR_PATTERN.find(path.fileName.toString())?.groupValues?.get(1)?.toIntOrNull(),
                    protected = path.toAbsolutePath().normalize() in protectedRuntimes.map { it.toAbsolutePath().normalize() },
                )
            }

        val categories =
            listOf(
                StorageCategoryId.INSTANCES to instancesDir,
                StorageCategoryId.VERSIONS to versionsDir,
                StorageCategoryId.LIBRARIES to dataDir.resolve("libraries"),
                StorageCategoryId.ASSETS to dataDir.resolve("assets"),
                StorageCategoryId.RUNTIME to runtimeDir,
                StorageCategoryId.CACHE to dataDir.resolve("cache"),
                StorageCategoryId.LOGS to dataDir.resolve("logs"),
                StorageCategoryId.PLUGINS to dataDir.resolve("plugins"),
            ).map { (id, path) -> StorageCategory(id, sizeOf(path, warnings)) }

        val total = sizeOf(dataDir, warnings)
        val known = categories.sumOf { it.bytes }
        val other = (total - known).coerceAtLeast(0)
        val allCategories = if (other > 0) categories + StorageCategory(StorageCategoryId.OTHER, other) else categories

        return StorageSnapshot(
            totalBytes = total,
            categories = allCategories,
            instances = instanceItems.sortedBy { it.name },
            gameVersions = versionItems.filter { it.baseVersionId == null },
            loaderVersions = versionItems.filter { it.baseVersionId != null },
            runtimes = runtimes.sortedBy { it.relativePath },
            warnings = warnings,
        )
    }

    /**
     * 删除一个版本目录。
     *
     * @param versionId 版本 id（必须是单段目录名）
     * @param runningVersionIds 正在运行游戏的版本 id
     */
    fun deleteVersion(versionId: String, runningVersionIds: Set<String> = emptySet()): DeleteOutcome {
        val target = safeChild(versionsDir, versionId) ?: return DeleteOutcome.Blocked(DeleteBlock.INVALID_TARGET)
        if (!Files.isDirectory(target)) return DeleteOutcome.Blocked(DeleteBlock.NOT_FOUND)
        if (versionId in runningVersionIds) return DeleteOutcome.Blocked(DeleteBlock.RUNNING)

        val warnings = mutableListOf<String>()
        val metas = readVersionMetas(warnings)
        val used = instancesProvider().filter { it.versionId == versionId }
        if (used.isNotEmpty()) {
            return DeleteOutcome.Blocked(DeleteBlock.USED_BY_INSTANCE, used.joinToString(", ") { it.name })
        }
        val children = metas.values.filter { it.baseVersionId == versionId }
        if (children.isNotEmpty()) {
            return DeleteOutcome.Blocked(DeleteBlock.INHERITED_BY, children.joinToString(", ") { it.id })
        }

        val bytes = sizeOf(target, warnings)
        return try {
            deleteTree(target)
            logger.info("已删除版本目录：{}（{} 字节）", target, bytes)
            DeleteOutcome.Deleted(bytes)
        } catch (e: IOException) {
            DeleteOutcome.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /**
     * 删除一个运行时目录。
     *
     * @param relativePath 相对 `runtime/` 的路径，形如 `macos-arm64/jdk-25`
     * @param protectedRuntimes 不允许删除的目录
     */
    fun deleteRuntime(relativePath: String, protectedRuntimes: Set<Path> = emptySet()): DeleteOutcome {
        val relative = runCatching { Path.of(relativePath).normalize() }.getOrNull()
            ?: return DeleteOutcome.Blocked(DeleteBlock.INVALID_TARGET)
        if (relative.isAbsolute || relative.nameCount != 2) return DeleteOutcome.Blocked(DeleteBlock.INVALID_TARGET)
        val target = runtimeDir.resolve(relative).normalize()
        if (!target.startsWith(runtimeDir.normalize())) return DeleteOutcome.Blocked(DeleteBlock.INVALID_TARGET)
        if (Files.isSymbolicLink(target)) return DeleteOutcome.Blocked(DeleteBlock.INVALID_TARGET)
        if (!Files.isDirectory(target)) return DeleteOutcome.Blocked(DeleteBlock.NOT_FOUND)
        if (protectedRuntimes.any { it.toAbsolutePath().normalize() == target.toAbsolutePath().normalize() }) {
            return DeleteOutcome.Blocked(DeleteBlock.PROTECTED)
        }

        val warnings = mutableListOf<String>()
        val bytes = sizeOf(target, warnings)
        return try {
            deleteTree(target)
            logger.info("已删除运行时目录：{}（{} 字节）", target, bytes)
            DeleteOutcome.Deleted(bytes)
        } catch (e: IOException) {
            DeleteOutcome.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /** 清空 `cache/` 内容（保留目录本身）。 */
    fun clearCache(): DeleteOutcome {
        val cache = dataDir.resolve("cache")
        if (!Files.isDirectory(cache)) return DeleteOutcome.Blocked(DeleteBlock.NOT_FOUND)
        val warnings = mutableListOf<String>()
        val bytes = sizeOf(cache, warnings)
        return try {
            Files.list(cache).use { stream ->
                stream.forEach { child ->
                    runCatching { deleteTree(child) }
                        .onFailure { logger.warn("清理缓存失败：{}", child, it) }
                }
            }
            logger.info("已清理缓存：{}（{} 字节）", cache, bytes)
            DeleteOutcome.Deleted(bytes)
        } catch (e: IOException) {
            DeleteOutcome.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private data class VersionMeta(
        val id: String,
        val baseVersionId: String?,
    )

    private fun readVersionMetas(warnings: MutableList<String>): Map<String, VersionMeta> {
        if (!Files.isDirectory(versionsDir)) return emptyMap()
        val result = mutableMapOf<String, VersionMeta>()
        try {
            Files.list(versionsDir).use { stream ->
                for (dir in stream) {
                    if (!Files.isDirectory(dir)) continue
                    val id = dir.fileName.toString()
                    val file = dir.resolve("$id.json")
                    if (!Files.isRegularFile(file)) {
                        warnings.add("版本 $id 缺少 JSON，按普通版本处理")
                        result[id] = VersionMeta(id, null)
                        continue
                    }
                    val meta =
                        runCatching {
                            val root = json.parseToJsonElement(Files.readString(file)).jsonObject
                            val base = root["inheritsFrom"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            VersionMeta(root["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: id, base)
                        }.getOrElse {
                            warnings.add("版本 $id 的 JSON 无法解析：${it.message}")
                            VersionMeta(id, null)
                        }
                    result[meta.id] = meta
                }
            }
        } catch (e: IOException) {
            warnings.add("无法读取版本目录：${e.message}")
        }
        return result
    }

    private fun listRuntimes(warnings: MutableList<String>): List<Path> {
        if (!Files.isDirectory(runtimeDir)) return emptyList()
        val result = mutableListOf<Path>()
        try {
            Files.list(runtimeDir).use { osStream ->
                for (osDir in osStream) {
                    if (!Files.isDirectory(osDir)) continue
                    try {
                        Files.list(osDir).use { stream ->
                            for (dir in stream) {
                                if (Files.isDirectory(dir)) result.add(runtimeDir.relativize(dir))
                            }
                        }
                    } catch (e: IOException) {
                        warnings.add("无法读取运行时目录：${osDir.fileName}")
                    }
                }
            }
        } catch (e: IOException) {
            warnings.add("无法读取运行时目录：${e.message}")
        }
        return result
    }

    private fun safeChild(parent: Path, name: String): Path? {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) return null
        val target = parent.resolve(name).normalize()
        return target.takeIf { it.startsWith(parent.normalize()) }
    }

    private fun sizeOf(path: Path, warnings: MutableList<String>): Long {
        if (!Files.exists(path)) return 0
        var total = 0L
        try {
            Files.walkFileTree(
                path,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (attrs.isRegularFile) total += attrs.size()
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        warnings += "无法读取 ${file.fileName}：${exc.message}"
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (e: IOException) {
            warnings += "统计失败 ${path.fileName}：${e.message}"
        }
        return total
    }

    private fun deleteTree(target: Path) {
        Files.walkFileTree(
            target,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private companion object {
        val MAJOR_PATTERN = Regex("""jdk-(\d+)""")
    }
}
