plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 插件独立版本；语言包只提供数据，不跟随启动器版本
version = "0.0.2"

// 注意：本插件**不**由 :app 打包，属于"示例语言包"，需单独构建后放进插件目录
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
    // 插件 JAR 命名：<pluginId>-<version>.jar
    archiveBaseName.set("dev.mmcl.locale.zh_tw")
}
