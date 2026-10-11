package dev.pulse.modelregistry

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Versioned, signed distilled-model distribution: manifests per channel, staged rollout, delta artifacts on CDN.
 *
 * Data store: PostgreSQL (model versions, rollout state), S3 + CDN (artifacts, bsdiff deltas)
 * Events emitted: models.version.published.v1, models.rollout.changed.v1
 * Contract: api/openapi.yaml. Layout follows catalog-service (domain / application / adapter / config).
 */
@SpringBootApplication
class ModelRegistryServiceApplication

fun main(args: Array<String>) {
    runApplication<ModelRegistryServiceApplication>(*args)
}
