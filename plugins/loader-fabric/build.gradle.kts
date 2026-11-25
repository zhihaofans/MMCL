plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// 插件独立版本，与启动器版本解耦（ARCHITECTURE.md §1）
version = "0.0.3"

// 只依赖 api（compileOnly，运行期由启动器提供）；HTTP/JSON 都用 JDK 与启动器自带的库。
dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.slf4j.api)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.serialization.json)

    testImplementation(project(":api"))
    testImplementation(libs.slf4j.api)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    // 插件 JAR 命名：<pluginId>-<version>.jar（ARCHITECTURE.md §9）
    archiveBaseName.set("dev.mmcl.loader.fabric")
}
