plugins {
    id("pulse.spring.service")
}

description = "Cohort profiles from DP-noised histograms, item embeddings (pgvector), optional server re-rank of candidates."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.kafka:spring-kafka")
}
