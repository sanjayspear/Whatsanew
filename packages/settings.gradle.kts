pluginManagement {
    includeBuild("../build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "packages"

// Shared, platform-agnostic contracts. Consumed by apps/mobile and services/ via
// composite-build substitution of `dev.pulse.packages:<name>`.
include(":ui-dsl")
