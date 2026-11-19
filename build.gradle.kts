import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// 版本号唯一来源：gradle.properties 的 version（见 AGENTS.md §0）
val launcherVersion = providers.gradleProperty("version").getOrElse("0.0.0-dev")

subprojects {
    group = "dev.mmcl"
    version = launcherVersion

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            // 用 JDK 27 编译，产出 17 字节码：启动器本身要能被 17+ 的 JRE 运行；
            // JDK 27 同时用于 jpackage（JDK < 27 的 macOS jpackage 拒绝 0.x 版本号）
            jvmToolchain(27)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

// 一键收集全部插件 JAR：./gradlew packagePlugins → build/plugins/
val packagePlugins =
    tasks.register<Sync>("packagePlugins") {
        group = "distribution"
        description = "构建全部插件并收集 JAR 到 build/plugins/"
        subprojects
            .filter { it.path.startsWith(":plugins:") }
            .forEach { plugin ->
                // 只收当前版本的 jar 产物，不要扫 build/libs 目录：
                // 旧版本的 JAR 会一直躺在那里，扫目录会把过期插件一起发布出去
                from(plugin.tasks.named("jar"))
            }
        into(layout.buildDirectory.dir("plugins"))
    }


// 插件版本一致性：build.gradle.kts 的 version 必须与 Plugin.version 相同（AGENTS.md §0）
val verifyPluginVersions =
    tasks.register("verifyPluginVersions") {
        group = "verification"
        description = "校验每个插件的 build.gradle.kts version 与 Plugin.version 一致"
        doLast {
            val pluginsDir = rootDir.resolve("plugins")
            val problems = mutableListOf<String>()
            pluginsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name }
                ?.forEach { dir ->
                    val buildFile = dir.resolve("build.gradle.kts")
                    if (!buildFile.isFile) return@forEach
                    val declared =
                        Regex("""(?:^|\n)version\s*=\s*"([^"]+)"""")
                            .find(buildFile.readText())?.groupValues?.get(1)
                    val declaredVersions =
                        dir.resolve("src/main/kotlin").walkTopDown()
                            .filter { it.isFile && it.extension == "kt" }
                            .mapNotNull { file ->
                                Regex("""override val version:\s*String\s*=\s*"([^"]+)"""")
                                    .find(file.readText())?.groupValues?.get(1)
                            }
                            .toList()
                    if (declared == null) {
                        problems += "${dir.name}: build.gradle.kts 里没有 version"
                    } else if (declaredVersions.isEmpty()) {
                        problems += "${dir.name}: 找不到 override val version"
                    } else if (declaredVersions.any { it != declared }) {
                        problems += "${dir.name}: build.gradle.kts=$declared，Plugin.version=$declaredVersions"
                    }
                }
            if (problems.isNotEmpty()) {
                throw GradleException("插件版本不一致：\n" + problems.joinToString("\n"))
            }
        }
    }

tasks.named("packagePlugins") { dependsOn(verifyPluginVersions) }
