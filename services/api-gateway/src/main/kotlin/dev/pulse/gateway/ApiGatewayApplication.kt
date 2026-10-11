package dev.pulse.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Edge: auth (DPoP-bound JWT), device attestation, rate limiting, REST BFF for mobile.
 *
 * Data store: Redis (rate-limit buckets, token denylist)
 * Events emitted: none (synchronous edge)
 * Contract: api/openapi.yaml. Layout follows catalog-service (domain / application / adapter / config).
 */
@SpringBootApplication
class ApiGatewayApplication

fun main(args: Array<String>) {
    runApplication<ApiGatewayApplication>(*args)
}
