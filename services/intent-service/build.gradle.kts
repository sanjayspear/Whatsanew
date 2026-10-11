plugins {
    id("pulse.spring.service")
}

description = "Server-side intent resolution for devices without an on-device LLM, and parity/eval of on-device output. Reuses the exact same SurfaceComposer as mobile (packages/ui-dsl)."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("dev.pulse.packages:ui-dsl:0.1.0")
}
