package dev.pulse.ingestion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Accepts batched, DP-noised, gzip signal batches from devices; validates, dedupes on batchId, produces to Kafka.
 *
 * Data store: Redis (batchId idempotency keys, 7-day TTL); raw batches land in S3 via Kafka Connect
 * Events emitted: signals.batch.accepted.v1
 * Contract: api/openapi.yaml. Layout follows catalog-service (domain / application / adapter / config).
 */
@SpringBootApplication
class IngestionServiceApplication

fun main(args: Array<String>) {
    runApplication<IngestionServiceApplication>(*args)
}
