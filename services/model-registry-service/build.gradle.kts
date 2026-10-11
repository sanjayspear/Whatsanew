plugins {
    id("pulse.spring.service")
}

description = "Versioned, signed distilled-model distribution: manifests per channel, staged rollout, delta artifacts on CDN."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
