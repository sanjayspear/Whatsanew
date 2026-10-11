package dev.pulse.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("No version '$alias' in libs.versions.toml") }.requiredVersion

internal fun VersionCatalog.library(alias: String) =
    findLibrary(alias).orElseThrow { IllegalStateException("No library '$alias' in libs.versions.toml") }

/**
 * Android targets are on by default. Pass `-Ppulse.android=false` to build KMP modules on a
 * machine without an Android SDK (backend CI runners, fresh laptops): desktop + iOS still compile.
 */
internal val Project.androidEnabled: Boolean
    get() = providers.gradleProperty("pulse.android").map(String::toBoolean).getOrElse(true)

/** `:shared:core` -> `dev.pulse.shared.core` */
internal val Project.derivedNamespace: String
    get() = "dev.pulse" + path.replace(':', '.').replace('-', '_')
