package dev.mmcl.core.builtin

import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.Logger

/**
 * 内置插件同步（`ARCHITECTURE.md` §9）。
 *
 * 构建期由 `:app` 把内置插件 JAR 与 `index.txt` 放进资源目录 `builtin-plugins/`；
 * 运行期这里把它们复制到 `<dataDir>/plugins/`，之后与用户手动放入的插件走同一条加载路径。
 * 内置插件与已存在文件大小一致时跳过复制，避免每次启动重写。
 */
object BuiltinPluginSync {
    /** 资源目录名，与 `:app` 的打包任务保持一致。 */
    const val RESOURCE_DIR: String = "builtin-plugins"

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
        var written = 0
        for (name in names) {
            if (!isPlainFileName(name)) {
                logger.warn("内置插件索引包含非法文件名，已跳过：{}", name)
                continue
            }
            val bytes =
                classLoader.getResourceAsStream("$RESOURCE_DIR/$name")?.use { it.readBytes() }
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
        return written
    }

    private fun isPlainFileName(name: String): Boolean =
        name != "." && name != ".." && !name.contains('/') && !name.contains('\\')
}
