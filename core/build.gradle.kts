plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":api"))
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 网络/真实启动冒烟默认关闭：用 -Pmmcl.netTests=1 / -Pmmcl.realLaunch=1 打开
tasks.test {
    listOf("mmcl.netTests", "mmcl.realLaunch").forEach { key ->
        systemProperty(key, (project.findProperty(key) as String?) ?: "0")
    }
}
