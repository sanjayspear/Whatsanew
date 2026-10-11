plugins {
    id("pulse.spring.service")
}

description = "Radius and polygon queries, metro-aware search (walk/metro isochrones), place gazetteer per region. PostGIS."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
