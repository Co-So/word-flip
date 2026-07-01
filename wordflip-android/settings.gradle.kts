pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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
