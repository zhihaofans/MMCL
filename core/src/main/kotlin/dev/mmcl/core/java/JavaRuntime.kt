package dev.mmcl.core.java

import java.nio.file.Path

/**
 * 检测到的一个 Java 运行时。
 *
 * @property executable `java` 可执行文件（Windows 为 `java.exe`）
 * @property majorVersion 大版本号，例如 21（`1.8.0` 归一成 8）
 * @property source 这个运行时是从哪找到的
 */
data class JavaRuntime(
    val executable: Path,
    val majorVersion: Int,
    val source: JavaRuntimeSource,
)

/** 运行时的来源，界面用它显示"从哪儿找到的"。 */
enum class JavaRuntimeSource {
    /** 设置项 `java.home` 手动指定。 */
    CONFIGURED,

    /** 启动器自带：`<dataDir>/runtime/<os>-<arch>/`。 */
    BUNDLED,

    /** 环境变量 `JAVA_HOME`。 */
    JAVA_HOME,

    /** `PATH` 里的 `java`。 */
    PATH,

    /** macOS `/usr/libexec/java_home -v <major>`。 */
    SYSTEM_MACOS,
}
