plugins {
    kotlin("multiplatform") version "1.9.22" apply false
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.spring") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
    kotlin("android") version "1.9.22" apply false

    id("org.springframework.boot") version "3.2.0" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
}

group = "com.cocro"
version = "0.1.0"
