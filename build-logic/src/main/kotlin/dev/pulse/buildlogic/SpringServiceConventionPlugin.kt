package dev.pulse.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Everything a Pulse Spring Boot microservice shares, so a new service's build file is
 * ~10 lines of service-specific dependencies. This is what lets `services/` grow to 20+
 * modules without copy-paste drift.
 */
class SpringServiceConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("org.jetbrains.kotlin.plugin.spring")
        pluginManager.apply("org.springframework.boot")
        pluginManager.apply("io.spring.dependency-management")

        group = "dev.pulse.services"

        extensions.getByType(KotlinJvmProjectExtension::class.java).apply {
            jvmToolchain(21)
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            }
        }

        dependencies {
            add("implementation", "org.springframework.boot:spring-boot-starter-actuator")
            add("implementation", "org.springframework.boot:spring-boot-starter-validation")
            add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin")
            add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
            add("implementation", "io.micrometer:micrometer-tracing-bridge-otel")
            add("implementation", "io.opentelemetry:opentelemetry-exporter-otlp")
            add("runtimeOnly", "io.micrometer:micrometer-registry-prometheus")

            add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
            add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
            add("testImplementation", libs.library("archunit-junit5"))
            add("testImplementation", libs.library("mockk"))
            add("testImplementation", platform(libs.library("testcontainers-bom")))
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging { events("failed", "skipped") }
        }
    }
}
