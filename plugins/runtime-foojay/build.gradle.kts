plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 插件独立版本，与启动器版本解耦（ARCHITECTURE.md §1）
version = "0.0.1"

// 注意：本插件**不**由 :app 打包（app/build.gradle.kts 的 builtinPlugins 里没有它），
// 必须单独构建后放进 <数据目录>/plugins/ 才会生效。
dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.slf4j.api)
    compileOnly(libs.kotlinx.coroutines.core)
    // JSON 解析复用启动器自带的同一个库（core 已依赖），插件不打包第三方库
    compileOnly(libs.kotlinx.serialization.json)

    testImplementation(project(":api"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    // 插件 JAR 命名：<pluginId>-<version>.jar
    archiveBaseName.set("dev.mmcl.runtime.foojay")
}
