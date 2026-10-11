package dev.pulse.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Platform-agnostic KMP library (JVM + iOS, no Android plugin).
 * Used by `packages/` contracts that are shared by mobile *and* Spring Boot services.
 * Android consumers resolve the JVM variant, so no Android SDK is ever needed to build these.
 */
class KmpPureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(libs.version("jvm-target").toInt())
            jvm()
            iosArm64()
            iosSimulatorArm64()
            applyPulseDefaults(this@with)
        }
    }
}

internal fun KotlinMultiplatformExtension.applyPulseDefaults(project: Project) {
    sourceSets.configureEach {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
    sourceSets.getByName("commonTest").dependencies {
        implementation(kotlin("test"))
        implementation(project.libs.library("kotlinx-coroutines-test"))
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
