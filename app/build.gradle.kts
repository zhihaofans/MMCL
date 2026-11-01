import java.util.zip.ZipFile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val launcherVersion = providers.gradleProperty("version").getOrElse("0.0.0-dev")

// ---------------------------------------------------------------------------
// 打包 JDK 解析
//
// jpackage 在 JDK 24~26 上拒绝首段为 0 的 app-version（JDK-8380634，JDK 27 已修复），
// 所以只有当“实际用于打包的 JDK”主版本落在 24..26 时，才把版本映射成 1.x。
// 解析顺序：-PpackageJdk=<JDK 目录> → 自动发现的 JDK 27 → 当前运行 Gradle 的 JDK。
//
// 两个坑（都踩过）：
// 1) launcherFor{...}.getOrNull() 在“找不到匹配工具链”时**不是**返回 null，而是抛
//    ToolchainProvisioningException。该表达式在配置阶段求值，一旦抛出，连 help/assemble 都跑不了。
// 2) Windows 上传 -PpackageJdk=C:\Program Files\... 若不加引号，会被 shell 按空格切成多个参数，
//    Gradle 把 "Files\Eclipse" 当成任务名，报 “Task 'Files\Eclipse' not found”，与 JDK 毫无关系。
// ---------------------------------------------------------------------------
val toolchains = extensions.getByType<JavaToolchainService>()

/** 允许传 JDK 根目录、bin 目录或 java(.exe) 路径；不合法返回 null。 */
fun normalizeJdkHome(raw: String?): File? {
    val text = raw?.trim()?.trim('"')?.trim('\'')?.takeIf { it.isNotEmpty() } ?: return null
    var dir = File(text)
    if (dir.isFile) dir = dir.parentFile ?: return null
    if (dir.name.equals("bin", ignoreCase = true)) dir = dir.parentFile ?: return null
    val exe = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
    return dir.takeIf { File(it, "bin/$exe").isFile }
}

/** 从 <jdk>/release 读 JAVA_VERSION，避开启动 JVM 的开销。 */
fun jdkMajorVersion(home: File): Int? {
    val release = File(home, "release")
    if (!release.isFile) return null
    val value =
        release.useLines { lines -> lines.firstOrNull { it.startsWith("JAVA_VERSION=") } }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?: return null
    return if (value.startsWith("1.")) {
        value.removePrefix("1.").substringBefore('.').toIntOrNull()
    } else {
        value.substringBefore('.').toIntOrNull()
    }
}

val requestedJdkRaw: String? = providers.gradleProperty("packageJdk").orNull
val requestedJdk: File? = normalizeJdkHome(requestedJdkRaw)
if (requestedJdkRaw != null && requestedJdk == null) {
    logger.warn(
        "忽略 -PpackageJdk=$requestedJdkRaw：不是有效的 JDK 目录（需要包含 bin${File.separator}java）。" +
            "Windows 下路径含空格必须整体加引号，例如：\n" +
            "  gradlew.bat build \"-PpackageJdk=C:\\Program Files\\Eclipse Adoptium\\jdk-21\"",
    )
}

val packagingJdkHome: File? =
    requestedJdk
        ?: runCatching {
            toolchains
                .launcherFor { languageVersion.set(JavaLanguageVersion.of(27)) }
                .getOrNull()
                ?.metadata
                ?.installationPath
                ?.asFile
        }.getOrNull()

val packagingJdkMajor: Int = packagingJdkHome?.let(::jdkMajorVersion) ?: Runtime.version().feature()

val hostOsName = System.getProperty("os.name").lowercase()

// 正常构建用 JDK 27，不做映射。只有手动 -PpackageJdk 指向 JDK < 27 且在 macOS 打包时，
// jpackage 会拒绝首段为 0 的版本号（实测 21/25 都拒），此时才映射 0.0.2 -> 1.0.2。
val needsVersionMapping: Boolean = hostOsName.contains("mac") && packagingJdkMajor < 27
val bundleVersion: String =
    run {
        val parts = launcherVersion.split(".")
        val major = parts.firstOrNull()?.toIntOrNull() ?: 0
        if (needsVersionMapping && major == 0) {
            "1." + parts.drop(1).joinToString(".").ifBlank { "0.0" }
        } else {
            launcherVersion
        }
    }

logger.info(
    "打包 JDK：${packagingJdkHome?.absolutePath ?: "<未指定，使用 Gradle 当前 JDK>"}，" +
        "主版本 $packagingJdkMajor，安装包版本 ${if (needsVersionMapping) bundleVersion else launcherVersion}",
)

dependencies {
    implementation(project(":api"))
    implementation(project(":api-ui"))
    implementation(project(":ui-kit"))
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)

    runtimeOnly(libs.logback.classic)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// ---------------------------------------------------------------------------
// 内置插件（ARCHITECTURE.md §9）：把插件 JAR 与 index.txt 放进资源目录
// builtin-plugins/，运行期由 core 的 BuiltinPluginSync 同步到 <dataDir>/plugins/
// ---------------------------------------------------------------------------
val builtinPlugins: Configuration = configurations.create("builtinPlugins") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    builtinPlugins(project(":plugins:auth-offline"))
    builtinPlugins(project(":plugins:ui-about"))
    builtinPlugins(project(":plugins:theme-sakura"))
}

val builtinResourcesDir = layout.buildDirectory.dir("generated/builtin-resources")

val prepareBuiltinPlugins = tasks.register<Sync>("prepareBuiltinPlugins") {
    group = "build"
    description = "收集内置插件 JAR 并生成 index.txt（Sync 保证不残留旧版本）"
    from(builtinPlugins)
    into(builtinResourcesDir.map { it.dir("builtin-plugins") })
    doLast {
        val dir = builtinResourcesDir.get().dir("builtin-plugins").asFile
        val jars =
            dir.listFiles { file -> file.isFile && file.extension.equals("jar", ignoreCase = true) }
                ?.map { it.name }
                ?.sorted()
                .orEmpty()
        dir.resolve("index.txt").writeText(jars.joinToString(separator = "\n", postfix = if (jars.isEmpty()) "" else "\n"))
    }
}

// 插件不允许打包 Compose：运行期由启动器提供，自带会导致类冲突与体积爆炸（UI_DESIGN.md §10）
val verifyPluginJars = tasks.register("verifyPluginJars") {
    group = "verification"
    description = "检查内置插件 JAR 是否误打包了 Compose 类"
    inputs.files(builtinPlugins)
    doLast {
        inputs.files.files
            .filter { it.extension.equals("jar", ignoreCase = true) }
            .forEach { jar ->
                ZipFile(jar).use { zip ->
                    val offending =
                        zip.entries().asSequence()
                            .map { it.name }
                            .filter { it.startsWith("androidx/compose/") }
                            .take(5)
                            .toList()
                    check(offending.isEmpty()) {
                        "插件 ${jar.name} 打包了 Compose 类，会导致类冲突：$offending"
                    }
                }
            }
    }
}

sourceSets["main"].resources.srcDir(builtinResourcesDir)

tasks.named<ProcessResources>("processResources") {
    dependsOn(prepareBuiltinPlugins)
    inputs.property("launcherVersion", launcherVersion)
    filesMatching("mmcl-build.properties") {
        expand("launcherVersion" to launcherVersion)
    }
}

tasks.named("check") {
    dependsOn(verifyPluginJars)
}

// 按宿主平台选打包格式：Windows 上 packageMsi 才能真正产出安装包（需要 WiX 3.x）
val hostTargetFormat =
    when {
        hostOsName.contains("win") -> org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
        hostOsName.contains("mac") -> org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg
        else -> org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
    }

compose.desktop {
    application {
        mainClass = "dev.mmcl.app.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.name=MMCL")
        packagingJdkHome?.let { javaHome = it.absolutePath }
        nativeDistributions {
            targetFormats(hostTargetFormat)
            packageName = "MMCL"
            packageVersion = if (needsVersionMapping) bundleVersion else launcherVersion
            description = "插件化的 Minecraft Java 启动器"
            vendor = "MMCL"
            macOS {
                bundleID = "dev.mmcl.launcher"
                dockName = "MMCL"
            }
            windows {
                // 一旦发布不要改：MSI 用它识别"同一产品的升级/卸载"
                upgradeUuid = "64609219-4A98-4B20-85FA-52296C41DC46"
                menuGroup = "MMCL"
                perUserInstall = true
                dirChooser = true
            }
            // jlink 默认会裁掉这些模块：logback 需要 java.naming，Compose/skiko 偶发需要 unsupported
            modules("java.naming", "java.sql", "java.net.http", "jdk.crypto.ec", "jdk.unsupported")
        }
    }
}
