pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        // Compose Multiplatform 的里程碑/开发版本镜像；稳定版仍以 Maven Central 为主
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "MMCL"

include(
    ":api",
    ":api-ui",
    ":ui-kit",
    ":core",
    ":app",
    ":plugins:auth-offline",
    ":plugins:hello",
    ":plugins:ui-about",
    ":plugins:theme-sakura",
    ":plugins:runtime-adoptium",
    ":plugins:source-mojang",
)
