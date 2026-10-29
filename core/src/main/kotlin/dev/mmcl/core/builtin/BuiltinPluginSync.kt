package dev.mmcl.core.builtin

import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.Logger

/**
 * 内置插件同步（`ARCHITECTURE.md` §9）。
 *
 * 构建期由 `:app` 把内置插件 JAR 与 `index.txt` 放进资源目录 `builtin-plugins/`；
 * 运行期这里把它们复制到 `<dataDir>/plugins/`，之后与用户手动放入的插件走同一条加载路径。
 *
 * 同步规则：
 * - 目标文件大小与资源一致时跳过；
 * - 记录一份清单 `.builtin-plugins.manifest`，下次同步时只清理**自己上次写过、这次不再存在**的旧 JAR，
 *   用户手动放入的插件永远不会被删；
 * - 拒绝任何带路径分隔符的文件名，防止索引被篡改后写出目录。
 */
object BuiltinPluginSync {
    /** 资源目录名，与 `:app` 的打包任务保持一致。 */
    const val RESOURCE_DIR: String = "builtin-plugins"

    private const val MANIFEST_NAME = ".builtin-plugins.manifest"

    /**
     * 执行同步。
     *
     * @param targetDir 目标目录，通常是 `<dataDir>/plugins`
     * @param logger 同步日志
     * @param classLoader 资源加载器，测试时可注入
     * @return 本次实际写入的 JAR 数量
     */
    fun sync(
        targetDir: Path,
        logger: Logger,
        classLoader: ClassLoader = BuiltinPluginSync::class.java.classLoader,
    ): Int {
        val index = classLoader.getResourceAsStream("$RESOURCE_DIR/index.txt")
        if (index == null) {
            logger.info("未发现内置插件索引 {}/index.txt，跳过同步", RESOURCE_DIR)
            return 0
        }
        val names =
            index.bufferedReader(Charsets.UTF_8).use { it.readLines() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        Files.createDirectories(targetDir)
        val previouslySynced = readManifest(targetDir)
        var written = 0
        for (name in names) {
            if (!isPlainFileName(name)) {
                logger.warn("内置插件索引包含非法文件名，已跳过：{}", name)
                continue
            }
            val bytes = classLoader.getResourceAsStream("$RESOURCE_DIR/$name")?.use { it.readBytes() }
            if (bytes == null) {
                logger.warn("内置插件资源缺失，已跳过：{}", name)
                continue
            }
            val target = targetDir.resolve(name)
            if (Files.exists(target) && Files.size(target) == bytes.size.toLong()) continue
            Files.write(target, bytes)
            logger.info("同步内置插件：{}", name)
            written++
        }
        pruneStale(targetDir, previouslySynced, names.toSet(), logger)
        writeManifest(targetDir, names)
        return written
    }

    private fun readManifest(targetDir: Path): Set<String> {
        val manifest = targetDir.resolve(MANIFEST_NAME)
        if (!Files.isRegularFile(manifest)) return emptySet()
        return Files.readAllLines(manifest, Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun writeManifest(targetDir: Path, names: List<String>) {
        val body = names.joinToString(separator = "\n", postfix = if (names.isEmpty()) "" else "\n")
        Files.writeString(targetDir.resolve(MANIFEST_NAME), body, Charsets.UTF_8)
    }

    private fun pruneStale(targetDir: Path, previous: Set<String>, current: Set<String>, logger: Logger) {
        for (stale in previous - current) {
            if (!isPlainFileName(stale)) continue
            val target = targetDir.resolve(stale)
            if (!Files.exists(target)) continue
            runCatching { Files.delete(target) }
                .onSuccess { logger.info("清理旧的内置插件：{}", stale) }
                .onFailure { logger.warn("清理旧内置插件失败：{}", stale, it) }
        }
    }

    private fun isPlainFileName(name: String): Boolean =
        name != "." && name != ".." && !name.contains('/') && !name.contains('\\')
}
