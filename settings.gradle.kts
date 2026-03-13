pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "cocro"

include(
    ":cocro-shared",
    ":cocro-bff",
    ":cocro-cmp",
)
