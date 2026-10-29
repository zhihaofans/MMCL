plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// UI 扩展契约：只有 app 与 UI 插件依赖本模块（ARCHITECTURE.md §11）
dependencies {
    api(project(":api"))
    api(compose.runtime)
    api(compose.foundation)
    api(compose.material3)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
