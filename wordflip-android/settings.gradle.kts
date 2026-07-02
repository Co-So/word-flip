pluginManagement {
    repositories {
        // 国内镜像优先，官方源作回退
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "wordflip-android"

include(
    ":app",
    ":core-network",
    ":core-model",
    ":core-ui",
    ":core-image",
    ":feature-auth",
    ":feature-today",
    ":feature-books",
    ":feature-groups",
    ":feature-study",
    ":feature-quiz",
    ":feature-stats",
    ":feature-settings",
    ":feature-snapshot",
)
