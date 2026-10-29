plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val launcherVersion = providers.gradleProperty("version").getOrElse("0.0.0-dev")

dependencies {
    implementation(project(":api"))
    implementation(project(":api-ui"))
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
    builtinPlugins(project(":plugins:hello"))
}

val builtinResourcesDir = layout.buildDirectory.dir("generated/builtin-resources")

val prepareBuiltinPlugins = tasks.register<Copy>("prepareBuiltinPlugins") {
    group = "build"
    description = "收集内置插件 JAR 并生成 index.txt"
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

sourceSets["main"].resources.srcDir(builtinResourcesDir)

tasks.named<ProcessResources>("processResources") {
    dependsOn(prepareBuiltinPlugins)
    inputs.property("launcherVersion", launcherVersion)
    filesMatching("mmcl-build.properties") {
        expand("launcherVersion" to launcherVersion)
    }
}

compose.desktop {
    application {
        mainClass = "dev.mmcl.app.MainKt"
        nativeDistributions {
            packageName = "MMCL"
            packageVersion = launcherVersion
            description = "插件化的 Minecraft Java 启动器"
            vendor = "MMCL"
        }
    }
}
