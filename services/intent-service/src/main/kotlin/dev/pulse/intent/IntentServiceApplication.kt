package dev.pulse.intent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Server-side intent resolution for devices without an on-device LLM, and parity/eval of on-device output. Reuses the exact same SurfaceComposer as mobile (packages/ui-dsl).
 *
 * Data store: none (stateless); prompt/eval logs to S3
 * Events emitted: intent.resolved.v1 (sampled, PII-redacted, for eval)
 * Contract: api/openapi.yaml. Layout follows catalog-service (domain / application / adapter / config).
 */
@SpringBootApplication
class IntentServiceApplication

fun main(args: Array<String>) {
    runApplication<IntentServiceApplication>(*args)
}
