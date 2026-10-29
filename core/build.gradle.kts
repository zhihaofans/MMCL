plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":api"))
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
