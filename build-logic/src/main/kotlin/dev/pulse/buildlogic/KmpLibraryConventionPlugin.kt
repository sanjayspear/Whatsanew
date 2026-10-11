package dev.pulse.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Mobile KMP module: Android + iOS + `desktop` (JVM).
 *
 * The desktop target is a deliberate dev/test harness: it lets the whole shared stack
 * (intent pipeline, renderer, components) run and be unit-tested on any laptop or CI runner
 * in seconds, without an emulator. It is never shipped.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        if (androidEnabled) pluginManager.apply("com.android.library")

        val jvmTarget = libs.version("jvm-target")
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(jvmTarget.toInt())
            if (androidEnabled) {
                androidTarget {
                    compilerOptions { this.jvmTarget.set(JvmTarget.fromTarget(jvmTarget)) }
                }
            }
            jvm("desktop")
            iosArm64()
            iosSimulatorArm64()
            applyPulseDefaults(this@with)
        }

        if (androidEnabled) {
            extensions.configure<LibraryExtension> {
                namespace = derivedNamespace
                compileSdk = libs.version("android-compileSdk").toInt()
                defaultConfig { minSdk = libs.version("android-minSdk").toInt() }
            }
        }
    }
}
