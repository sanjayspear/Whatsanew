package dev.pulse.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** [KmpLibraryConventionPlugin] + Compose Multiplatform. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("pulse.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    }
}
