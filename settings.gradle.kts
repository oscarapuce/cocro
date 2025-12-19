pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    plugins {
        kotlin("multiplatform") version "1.9.10"
        kotlin("jvm") version "1.9.10"
        kotlin("plugin.spring") version "1.9.10"

        id("org.springframework.boot") version "3.2.0"
        id("io.spring.dependency-management") version "1.1.4"
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
//    ":cocro-cmp"
)
