pluginManagement {
    includeBuild("../../build-logic")
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
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "pulse-mobile"

include(":shared:core")   // AI engine, intent pipeline, data, signals, model updates (no UI)
include(":shared:ui")     // Design system, component registry, DSL renderer, screens
include(":desktopApp")    // Dev harness + headless screenshot renderer (never shipped)

if (providers.gradleProperty("pulse.android").map(String::toBoolean).getOrElse(true)) {
    include(":androidApp")
}

includeBuild("../../packages")
