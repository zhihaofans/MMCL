plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 插件独立版本，与启动器版本解耦（ARCHITECTURE.md §1）
version = "0.0.1"

// 本插件不由 :app 内置，需要单独构建后放进 <数据目录>/plugins/
dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.slf4j.api)

    testImplementation(project(":api"))
    testImplementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    // 插件 JAR 命名：<pluginId>-<version>.jar
    archiveBaseName.set("dev.mmcl.runtime.adoptium")
}
