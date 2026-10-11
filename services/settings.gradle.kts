pluginManagement {
    includeBuild("../build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "services"

// Auto-discovery: any `services/<name>/build.gradle.kts` is a module. Adding service #21 is
// `mkdir` + a 10-line build file. No edits here, no merge conflicts between teams.
rootDir.listFiles()!!
    .filter { it.isDirectory && it.resolve("build.gradle.kts").exists() }
    .sortedBy { it.name }
    .forEach { include(":${it.name}") }

includeBuild("../packages")
