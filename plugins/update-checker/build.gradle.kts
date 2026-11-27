plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// 插件独立版本，与启动器版本解耦（ARCHITECTURE.md §1）
version = "0.0.1"

// 内置 UI 插件：只依赖 ui-kit（传递 api / api-ui），Compose 由启动器提供。
dependencies {
    compileOnly(project(":ui-kit"))
    compileOnly(libs.slf4j.api)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.serialization.json)

    testImplementation(project(":ui-kit"))
    testImplementation(libs.slf4j.api)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    archiveBaseName.set("dev.mmcl.update.checker")
}
