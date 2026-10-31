import java.util.zip.ZipFile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val launcherVersion = providers.gradleProperty("version").getOrElse("0.0.0-dev")

// jpackage 在 JDK 24~26 上拒绝首段为 0 的 app-version（JDK-8380634，JDK 27 已修复）。
// 打包优先用本机装好的 JDK 27（Gradle 会自动发现 ~/Library/Java/JavaVirtualMachines 下的 JDK）；
// 没有 27 时退回当前 JDK，并只在这条退路上把安装包版本映射成 1.x。
val toolchains = extensions.getByType<JavaToolchainService>()
val packagingJdkHome: String? =
    providers.gradleProperty("packageJdk").orNull?.takeIf { it.isNotBlank() }
        ?: toolchains
            .launcherFor { languageVersion.set(JavaLanguageVersion.of(27)) }
            .getOrNull()
            ?.metadata
            ?.installationPath
            ?.asFile
            ?.absolutePath

// 兜底映射：0.0.1 -> 1.0.1（仅在找不到 JDK 27 时使用）
val bundleVersion: String =
    run {
        val parts = launcherVersion.split(".")
        val major = parts.firstOrNull()?.toIntOrNull() ?: 0
        if (major == 0) "1." + parts.drop(1).joinToString(".").ifBlank { "0.0" } else launcherVersion
    }

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
    builtinPlugins(project(":plugins:hello"))
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

compose.desktop {
    application {
        mainClass = "dev.mmcl.app.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.name=MMCL")
        if (packagingJdkHome != null) {
            javaHome = packagingJdkHome
        }
        nativeDistributions {
            // 先只出 macOS（当前优先平台）；Windows/Linux 以后再按平台分支
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "MMCL"
            // 用 JDK 27 打包时可以保持真实版本；否则退回 1.x 映射
            packageVersion = if (packagingJdkHome != null) launcherVersion else bundleVersion
            description = "插件化的 Minecraft Java 启动器"
            vendor = "MMCL"
            macOS {
                bundleID = "dev.mmcl.launcher"
                dockName = "MMCL"
            }
            // jlink 默认会裁掉这些模块：logback 需要 java.naming，Compose/skiko 偶发需要 unsupported
            modules("java.naming", "java.sql", "java.net.http", "jdk.crypto.ec", "jdk.unsupported")
        }
    }
}
