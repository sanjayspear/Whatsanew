plugins {
    id("pulse.spring.service")
}

description = "Push (FCM/APNs), email and in-app notifications: 'a play you would like opens tonight'. Quiet hours and frequency caps."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
}
