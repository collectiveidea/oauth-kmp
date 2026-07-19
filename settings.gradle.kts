enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions a matching JDK for the Kotlin toolchain (jvmToolchain(24)) when one
    // isn't installed locally, so the toolchain resolves without a manual JDK install.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        // For krypt SecureRandom
        maven { url = uri("https://repo.repsy.io/mvn/chrynan/public") }
    }
}

rootProject.name = "oauth-kmp"

include(":oauth-core")
project(":oauth-core").name = "oauth-core"
