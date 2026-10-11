plugins {
    id("pulse.spring.service")
}

description = "Accepts batched, DP-noised, gzip signal batches from devices; validates, dedupes on batchId, produces to Kafka."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
}
