package dev.mmcl.core.java

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.api.service.SettingsService
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.slf4j.Logger

private val defaultCommandRunner: (List<String>) -> String? = { command ->
    runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor(5, TimeUnit.SECONDS)) text else null
    }.getOrNull()
}

/**
 * Java 运行时探测。
 *
 * 收集全部候选运行时（大版本 ≥ 要求），然后按「精确优先、其次最接近」挑选：
 * 1. 设置项 `java.home`（用户手动指定）
 * 2. 启动器自带 `<dataDir>/runtime/<os>-<arch>/`
 * 3. 环境变量 `JAVA_HOME`
 * 4. `PATH` 里的 `java`
 * 5. macOS 上兜底 `/usr/libexec/java_home -v <major>`
 *
 * 之所以不直接用「第一个够用的」：系统里同时装了 25/27 而版本只要 21 时，
 * 拿 27 跑老版本会触发 LWJGL/JNA 的兼容警告甚至崩溃；优先精确版本最稳。
 *
 * 版本识别只读 JDK 目录下的 `release` 文件（`JAVA_VERSION="21.0.13"`），
 * 不执行 `java -version`：避免拉起进程，也避开各平台输出格式差异。
 *
 * 本轮不做：下载运行时（需要运行时来源插件）、Windows 注册表、架构匹配校验。
 *
 * @param dataDir 启动器数据目录，用于定位自带运行时
 * @param env 环境变量读取，测试可注入
 * @param pathLookup 在 PATH 里找 `java` 可执行文件；默认按 [env] 里的 PATH 查找
 * @param commandRunner 执行外部命令（macOS 的 java_home 兜底），返回 stdout；失败返回 `null`
 */
class JavaRuntimeDetector(
    private val dataDir: Path,
    private val os: OperatingSystem,
    private val arch: Architecture,
    private val settings: SettingsService,
    private val logger: Logger,
    private val env: (String) -> String? = { System.getenv(it) },
    private val pathLookup: (() -> Path?)? = null,
    private val commandRunner: (List<String>) -> String? = defaultCommandRunner,
) {
    /**
     * 找一个大版本 ≥ [requiredMajor] 的运行时。
     *
     * @return 找不到返回 `null`（调用方负责提示"缺少 Java"）
     */
    fun detect(requiredMajor: Int): JavaRuntime? {
        val runtimes =
            candidates(requiredMajor).mapNotNull { (home, source) -> probe(home, source, requiredMajor) }
        if (runtimes.isEmpty()) return null

        // 精确版本优先（同一版本多个来源时保留候选顺序）
        runtimes.firstOrNull { it.majorVersion == requiredMajor }?.let { return it }

        // 没有精确版本时选「最接近的更高版本」，不要无谓地拿最新 JDK 跑老版本
        val closest = runtimes.minByOrNull { it.majorVersion }
        if (closest != null) {
            logger.info(
                "没有 Java {}，改用最接近的 Java {}（{}）",
                requiredMajor,
                closest.majorVersion,
                closest.executable,
            )
        }
        return closest
    }

    /** 自带运行时目录里的全部候选 JDK 主目录。 */
    fun bundledHomes(): List<Path> {
        val root = dataDir.resolve("runtime").resolve("${os.name.lowercase()}-${arch.name.lowercase()}")
        if (!Files.isDirectory(root)) return emptyList()
        val homes = mutableListOf<Path>()
        homeIfValid(root)?.let { homes.add(it) }
        runCatching {
            Files.list(root).use { stream ->
                stream
                    .filter { Files.isDirectory(it) }
                    .sorted()
                    .forEach { child -> homeIfValid(child)?.let { homes.add(it) } }
            }
        }.onFailure { logger.warn("扫描自带运行时目录失败：{}", it.message) }
        return homes
    }

    private fun candidates(requiredMajor: Int): List<Pair<Path, JavaRuntimeSource>> {
        val result = mutableListOf<Pair<Path, JavaRuntimeSource>>()

        settings.getString(SETTING_JAVA_HOME)?.takeIf { it.isNotBlank() }?.let { configured ->
            runCatching { Path.of(configured) }.getOrNull()?.let { result += it to JavaRuntimeSource.CONFIGURED }
        }

        bundledHomes().forEach { result += it to JavaRuntimeSource.BUNDLED }

        env("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { javaHome ->
            runCatching { Path.of(javaHome) }.getOrNull()?.let { result += it to JavaRuntimeSource.JAVA_HOME }
        }

        val javaFromPath = (pathLookup ?: ::lookupOnPath).invoke()
        if (javaFromPath != null) {
            val home = javaFromPath.parent?.parent ?: javaFromPath
            result += home to JavaRuntimeSource.PATH
        }

        if (os == OperatingSystem.MACOS) {
            macOsJavaHome(requiredMajor)?.let { result += it to JavaRuntimeSource.SYSTEM_MACOS }
        }

        return result
    }

    private fun probe(home: Path, source: JavaRuntimeSource, requiredMajor: Int): JavaRuntime? {
        val executable = executableIn(home) ?: return null
        val major =
            readMajorVersion(home) ?: run {
                logger.debug("跳过 {}：缺少可解析的 release 文件", home)
                return null
            }
        if (major < requiredMajor) {
            logger.debug("跳过 {}：Java {} < 需要的 {}", home, major, requiredMajor)
            return null
        }
        return JavaRuntime(executable = executable, majorVersion = major, source = source)
    }

    private fun homeIfValid(dir: Path): Path? {
        if (executableIn(dir) != null) return dir
        val macHome = dir.resolve("Contents").resolve("Home")
        return if (executableIn(macHome) != null) macHome else null
    }

    private fun executableIn(home: Path): Path? {
        val exe = home.resolve("bin").resolve(executableName())
        return if (Files.isRegularFile(exe)) exe else null
    }

    private fun executableName(): String = if (os == OperatingSystem.WINDOWS) "java.exe" else "java"

    private fun lookupOnPath(): Path? {
        val path = env("PATH") ?: return null
        val executable = executableName()
        for (entry in path.split(File.pathSeparatorChar)) {
            if (entry.isBlank()) continue
            val candidate = runCatching { Path.of(entry).resolve(executable) }.getOrNull() ?: continue
            if (Files.isRegularFile(candidate)) return candidate
        }
        return null
    }

    private fun macOsJavaHome(requiredMajor: Int): Path? {
        val output = commandRunner(listOf("/usr/libexec/java_home", "-v", requiredMajor.toString())) ?: return null
        val path = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        return runCatching { Path.of(path) }.getOrNull()
    }

    companion object {
        /** 设置键：手动指定 JDK 目录。 */
        const val SETTING_JAVA_HOME: String = "java.home"
    }
}

/**
 * 从 JDK 的 `release` 文件解析大版本；没有该文件返回 `null`。
 *
 * 兼容 `JAVA_VERSION="1.8.0_392"`（→ 8）与 `JAVA_VERSION="21.0.13"`（→ 21）。
 */
internal fun readMajorVersion(home: Path): Int? {
    val release = home.resolve("release")
    if (!Files.isRegularFile(release)) return null
    val line =
        runCatching { Files.readAllLines(release, Charsets.UTF_8) }
            .getOrDefault(emptyList())
            .firstOrNull { it.trimStart().startsWith("JAVA_VERSION=") }
            ?: return null
    return majorOfJavaVersion(line.substringAfter('=').trim().trim('"'))
}

/** 把 `release` 里的版本串归一成大版本号。 */
internal fun majorOfJavaVersion(version: String): Int? {
    if (version.isBlank()) return null
    val withoutLegacyPrefix = version.removePrefix("1.")
    return withoutLegacyPrefix.takeWhile { it.isDigit() }.toIntOrNull()
}
