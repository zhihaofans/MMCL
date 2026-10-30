package dev.mmcl.core.java

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.settings.InMemorySettingsService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.slf4j.LoggerFactory

class JavaRuntimeDetectorTest {
    private val logger = LoggerFactory.getLogger("test")
    private lateinit var dataDir: Path
    private lateinit var temp: Path
    private val settings = InMemorySettingsService()

    @BeforeTest
    fun setUp() {
        temp = Files.createTempDirectory("mmcl-java-test")
        dataDir = temp.resolve("data")
        Files.createDirectories(dataDir)
    }

    @AfterTest
    fun tearDown() {
        temp.toFile().deleteRecursively()
    }

    /** 造一个假 JDK：`<root>/jdk-<major>/bin/java` + `release`。 */
    private fun fakeJdk(name: String, major: Int): Path {
        val home = temp.resolve(name)
        Files.createDirectories(home.resolve("bin"))
        Files.writeString(home.resolve("bin").resolve("java"), "")
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"$major.0.1\"\n")
        return home
    }

    private fun detector(
        os: OperatingSystem = OperatingSystem.MACOS,
        env: Map<String, String> = emptyMap(),
        pathJava: Path? = null,
        commandOutput: String? = null,
    ) = JavaRuntimeDetector(
        dataDir = dataDir,
        os = os,
        arch = Architecture.ARM64,
        settings = settings,
        logger = logger,
        env = { env[it] },
        pathLookup = { pathJava },
        commandRunner = { commandOutput },
    )

    @Test
    fun `设置里指定的 JDK 优先`() {
        val configured = fakeJdk("configured-21", 21)
        val fromEnv = fakeJdk("env-17", 17)
        settings.putString(JavaRuntimeDetector.SETTING_JAVA_HOME, configured.toString())

        val runtime = detector(env = mapOf("JAVA_HOME" to fromEnv.toString())).detect(21)

        assertEquals(JavaRuntimeSource.CONFIGURED, runtime?.source)
        assertEquals(21, runtime?.majorVersion)
        assertEquals(configured.resolve("bin/java"), runtime?.executable)
    }

    @Test
    fun `版本不够时继续找下一个来源`() {
        val tooOld = fakeJdk("configured-8", 8)
        val good = fakeJdk("env-17", 17)
        settings.putString(JavaRuntimeDetector.SETTING_JAVA_HOME, tooOld.toString())

        val runtime = detector(env = mapOf("JAVA_HOME" to good.toString())).detect(17)

        assertEquals(JavaRuntimeSource.JAVA_HOME, runtime?.source)
        assertEquals(17, runtime?.majorVersion)
    }

    @Test
    fun `自带运行时目录会被识别`() {
        val bundled = dataDir.resolve("runtime").resolve("macos-arm64").resolve("jdk-21")
        Files.createDirectories(bundled.resolve("bin"))
        Files.writeString(bundled.resolve("bin/java"), "")
        Files.writeString(bundled.resolve("release"), "JAVA_VERSION=\"21.0.4\"\n")

        val runtime = detector().detect(21)

        assertEquals(JavaRuntimeSource.BUNDLED, runtime?.source)
        assertEquals(bundled.resolve("bin/java"), runtime?.executable)
    }

    @Test
    fun `PATH 里的 java 作为兜底`() {
        val home = fakeJdk("path-17", 17)

        val runtime = detector(pathJava = home.resolve("bin/java")).detect(17)

        assertEquals(JavaRuntimeSource.PATH, runtime?.source)
    }

    @Test
    fun `macOS 用 java_home 兜底`() {
        val home = fakeJdk("system-21", 21)

        val runtime =
            detector(commandOutput = "${home}\n").detect(21)

        assertEquals(JavaRuntimeSource.SYSTEM_MACOS, runtime?.source)
    }

    @Test
    fun `非 macOS 不调用 java_home`() {
        val home = fakeJdk("system-21", 21)

        val runtime = detector(os = OperatingSystem.LINUX, commandOutput = "${home}\n").detect(21)

        assertNull(runtime)
    }

    @Test
    fun `都找不到时返回 null`() {
        assertNull(detector().detect(21))
    }

    @Test
    fun `没有 release 文件的目录不会被误认`() {
        val home = temp.resolve("no-release")
        Files.createDirectories(home.resolve("bin"))
        Files.writeString(home.resolve("bin/java"), "")
        settings.putString(JavaRuntimeDetector.SETTING_JAVA_HOME, home.toString())

        assertNull(detector().detect(17))
    }

    @Test
    fun `版本串归一`() {
        assertEquals(8, majorOfJavaVersion("1.8.0_392"))
        assertEquals(21, majorOfJavaVersion("21.0.13"))
        assertEquals(17, majorOfJavaVersion("17"))
        assertNull(majorOfJavaVersion(""))
    }
}
