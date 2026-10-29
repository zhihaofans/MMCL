plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 插件独立版本，与启动器版本解耦（ARCHITECTURE.md §1）
version = "0.0.1"

// 插件只依赖 api（compileOnly，运行期由启动器提供），不得依赖 core 或其它插件
dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.slf4j.api)

    testImplementation(project(":api"))
    testImplementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    // 插件 JAR 命名：<pluginId>-<version>.jar（ARCHITECTURE.md §9）
    archiveBaseName.set("dev.mmcl.plugin.hello")
}
