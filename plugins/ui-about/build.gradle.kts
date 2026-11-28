plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// 插件独立版本，与启动器版本解耦（ARCHITECTURE.md §1）
version = "0.0.3"

dependencies {
    compileOnly(project(":ui-kit"))
    compileOnly(libs.slf4j.api)

    testImplementation(project(":ui-kit"))
    testImplementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    // 插件 JAR 命名：<pluginId>-<version>.jar（ARCHITECTURE.md §9）
    archiveBaseName.set("dev.mmcl.ui.about")
}
