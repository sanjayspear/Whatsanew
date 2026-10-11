package dev.pulse.personalization

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Cohort profiles from DP-noised histograms, item embeddings (pgvector), optional server re-rank of candidates.
 *
 * Data store: PostgreSQL + pgvector (item embeddings, cohort vectors), Redis (hot cohort cache)
 * Events emitted: personalization.cohort.updated.v1
 * Contract: api/openapi.yaml. Layout follows catalog-service (domain / application / adapter / config).
 */
@SpringBootApplication
class PersonalizationServiceApplication

fun main(args: Array<String>) {
    runApplication<PersonalizationServiceApplication>(*args)
}
