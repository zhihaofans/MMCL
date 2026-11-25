plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// 插件独立版本，与启动器版本解耦（ARCHITECTURE.md §1）
version = "0.0.3"

// 插件只依赖 api（compileOnly，运行期由启动器提供）；带账户页，所以额外依赖 ui-kit。
// JSON 与协程复用启动器自带的同一个库（core 已依赖），插件不打包第三方库。
dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":ui-kit"))
    compileOnly(libs.slf4j.api)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.serialization.json)

    testImplementation(project(":api"))
    testImplementation(project(":ui-kit"))
    testImplementation(libs.slf4j.api)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    // 插件 JAR 命名：<pluginId>-<version>.jar（ARCHITECTURE.md §9）
    archiveBaseName.set("dev.mmcl.auth.microsoft")
}
