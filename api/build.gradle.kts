plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

// api 是纯契约：只能有接口、数据类、事件、异常，不含实现（AGENTS.md §3）
dependencies {
    api(libs.slf4j.api)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
