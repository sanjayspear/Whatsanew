package dev.pulse.geo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Radius and polygon queries, metro-aware search (walk/metro isochrones), place gazetteer per region. PostGIS.
 *
 * Data store: PostgreSQL + PostGIS (places, metro graph); gazetteer packs exported to S3 for devices
 * Events emitted: geo.gazetteer.published.v1
 * Contract: api/openapi.yaml. Layout follows catalog-service (domain / application / adapter / config).
 */
@SpringBootApplication
class GeoServiceApplication

fun main(args: Array<String>) {
    runApplication<GeoServiceApplication>(*args)
}
