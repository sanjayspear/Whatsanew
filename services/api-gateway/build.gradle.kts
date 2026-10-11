plugins {
    id("pulse.spring.service")
}

description = "Edge: auth (DPoP-bound JWT), device attestation, rate limiting, REST BFF for mobile."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
}
