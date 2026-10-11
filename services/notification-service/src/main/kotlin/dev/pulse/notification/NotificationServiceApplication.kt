package dev.pulse.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Push (FCM/APNs), email and in-app notifications: "a play you would like opens tonight". Quiet hours and frequency caps.
 *
 * Data store: PostgreSQL (device tokens, preferences), Redis (frequency caps)
 * Events emitted: notifications.sent.v1
 * Contract: api/openapi.yaml. Layout follows catalog-service (domain / application / adapter / config).
 */
@SpringBootApplication
class NotificationServiceApplication

fun main(args: Array<String>) {
    runApplication<NotificationServiceApplication>(*args)
}
