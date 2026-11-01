package dev.mmcl.core.instance

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.Logger

/**
 * 实例管理：创建 / 删除 / 列举。
 *
 * 实例目录结构：
 * ```
 * <instancesDir>/<id>/          ← game_directory
 * ├── saves/  mods/  config/    ← 玩家自己的东西
 * └── ...
 * ```
 * 共享的 `libraries` / `assets` / `versions` 不在实例目录里，见 [Instance] 的说明。
 *
 * @param instancesDir 实例根目录，通常 `<dataDir>/instances`
 * @param clock 创建时间来源，测试可注入
 */
class InstanceService(
    private val store: InstanceStore,
    private val instancesDir: Path,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** 当前全部实例，按创建时间升序。 */
    fun list(): List<Instance> = store.load().sortedBy { it.createdAtEpochMillis }

    /** 按"最近启动优先"排序（没启动过的按创建时间倒序排在后面），用于首页。 */
    fun recent(limit: Int = 3): List<Instance> =
        store.load()
            .sortedWith(
                compareByDescending<Instance> { it.lastLaunchedAtEpochMillis }
                    .thenByDescending { it.createdAtEpochMillis },
            )
            .take(limit)

    /** 记录一次成功启动；实例不存在时返回 `false`。 */
    fun markLaunched(id: String): Boolean {
        val existing = store.load()
        val target = existing.firstOrNull { it.id == id } ?: return false
        val updated = target.copy(lastLaunchedAtEpochMillis = clock())
        store.save(existing.map { if (it.id == id) updated else it })
        return true
    }

    /** 实例的游戏目录。 */
    fun directoryOf(instance: Instance): Path = instancesDir.resolve(instance.id)

    /**
     * 新建实例（目录立即创建）。
     *
     * @param versionId 绑定的版本
     * @param preferredName 展示名，默认用版本号；重名自动加 ` 2`、` 3`
     */
    fun create(versionId: String, preferredName: String? = null): Instance {
        val existing = store.load()
        val baseName = (preferredName ?: versionId).trim().ifBlank { versionId }
        val instance =
            Instance(
                id = uniqueId(versionId, existing),
                name = uniqueName(baseName, existing),
                versionId = versionId,
                createdAtEpochMillis = clock(),
            )
        createDirectories(directoryOf(instance))
        store.save(existing + instance)
        logger.info("已创建实例 {}（{}，版本 {}）", instance.name, instance.id, instance.versionId)
        return instance
    }

    /**
     * 删除实例：连同实例目录（含存档）一起删除。
     *
     * @return 实例不存在时返回 `false`
     */
    fun delete(id: String): Boolean {
        val existing = store.load()
        val target = existing.firstOrNull { it.id == id } ?: return false
        val directory = directoryOf(target)
        runCatching { deleteRecursively(directory) }
            .onFailure { logger.warn("删除实例目录失败：{}（{}）", directory, it.message) }
        store.save(existing - target)
        logger.info("已删除实例 {}（{}）", target.name, target.id)
        return true
    }

    private fun createDirectories(directory: Path) {
        Files.createDirectories(directory)
        listOf("saves", "mods", "config").forEach { name ->
            runCatching { Files.createDirectories(directory.resolve(name)) }
                .onFailure { logger.warn("创建实例子目录失败：{}（{}）", name, it.message) }
        }
    }

    private fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun uniqueId(versionId: String, existing: List<Instance>): String {
        val base = sanitize(versionId)
        if (existing.none { it.id == base }) return base
        var index = 2
        while (existing.any { it.id == "$base-$index" }) index++
        return "$base-$index"
    }

    private fun uniqueName(base: String, existing: List<Instance>): String {
        if (existing.none { it.name == base }) return base
        var index = 2
        while (existing.any { it.name == "$base $index" }) index++
        return "$base $index"
    }

    private fun sanitize(raw: String): String =
        raw.trim().ifBlank { "instance" }.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '-' }.joinToString("")
}
