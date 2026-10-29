plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// UI 公共组件：只有 app 与 UI 插件依赖它；它传递 api-ui，不依赖 core
dependencies {
    api(project(":api-ui"))
    api(compose.runtime)
    api(compose.foundation)
    api(compose.material3)
    api(libs.compose.icons.core)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
