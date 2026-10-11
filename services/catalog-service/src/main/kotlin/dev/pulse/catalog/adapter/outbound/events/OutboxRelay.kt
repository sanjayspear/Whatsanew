package dev.pulse.catalog.adapter.outbound.events

import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Polls the outbox and forwards to Kafka. `FOR UPDATE SKIP LOCKED` lets several replicas relay
 * in parallel without double-sending the same row. At ~10x scale, swap this for Debezium CDC on
 * the outbox table; the publisher side doesn't change.
 */
@Component
@ConditionalOnProperty("pulse.outbox.relay.enabled", havingValue = "true")
class OutboxRelay(
    private val jdbc: JdbcClient,
    private val kafka: KafkaTemplate<String, String>,
    private val tx: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${pulse.outbox.relay.interval:PT1S}")
    fun relay() {
        tx.executeWithoutResult {
            val batch = jdbc.sql(
                """
                SELECT id, topic, message_key, payload::text AS payload FROM outbox
                WHERE published_at IS NULL ORDER BY occurred_at LIMIT 100
                FOR UPDATE SKIP LOCKED
                """.trimIndent(),
            ).query { rs, _ ->
                OutboxRow(rs.getObject("id", UUID::class.java), rs.getString("topic"), rs.getString("message_key"), rs.getString("payload"))
            }.list()

            batch.forEach { row ->
                kafka.send(row.topic, row.key, row.payload).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                jdbc.sql("UPDATE outbox SET published_at = now() WHERE id = :id").param("id", row.id).update()
            }
            if (batch.isNotEmpty()) log.debug("relayed {} outbox events", batch.size)
        }
    }

    private data class OutboxRow(val id: UUID, val topic: String, val key: String, val payload: String)

    private companion object {
        val SEND_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
