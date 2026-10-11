package dev.pulse.catalog.adapter.outbound.events

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pulse.catalog.application.port.outbound.ListingEventPublisher
import dev.pulse.catalog.domain.ListingEvent
import java.sql.Timestamp
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * Transactional outbox: the event row commits atomically with the listing row. Kafka being
 * down never loses an event or publishes one for a rolled-back write. [OutboxRelay] ships rows
 * to Kafka afterwards (at-least-once; consumers dedupe on the CloudEvent `id`).
 */
@Component
class OutboxEventPublisher(
    private val jdbc: JdbcClient,
    private val json: ObjectMapper,
) : ListingEventPublisher {

    override fun publish(event: ListingEvent) {
        val envelope = CloudEvents.envelope(event, eventId = UUID.randomUUID())
        jdbc.sql(
            """
            INSERT INTO outbox (id, topic, message_key, event_type, payload, occurred_at)
            VALUES (:id, :topic, :key, :type, CAST(:payload AS jsonb), :occurredAt)
            """.trimIndent(),
        )
            .param("id", envelope.id)
            .param("topic", CloudEvents.TOPIC)
            .param("key", event.listingId.toString())
            .param("type", envelope.type)
            .param("payload", json.writeValueAsString(envelope))
            .param("occurredAt", Timestamp.from(event.occurredAt))
            .update()
    }
}
