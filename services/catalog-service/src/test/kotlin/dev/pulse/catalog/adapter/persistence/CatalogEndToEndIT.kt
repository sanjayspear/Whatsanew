package dev.pulse.catalog.adapter.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Real HTTP -> real Postgres/PostGIS (Flyway-migrated) -> outbox, end to end. Skipped
 * automatically on machines without Docker; always runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["pulse.outbox.relay.enabled=false"],
)
class CatalogEndToEndIT(
    @Autowired private val http: TestRestTemplate,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val json: ObjectMapper,
) {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
    }

    private fun create(title: String, lat: Double, lng: Double, category: String = "food.cafe"): String {
        val body = mapOf(
            "title" to mapOf("en" to title),
            "category" to category,
            "location" to mapOf("lat" to lat, "lng" to lng),
            "regionCode" to "IN-KA-BLR",
            "timeZone" to "Asia/Kolkata",
        )
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = http.postForEntity("/v1/listings", HttpEntity(json.writeValueAsString(body), headers), Map::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        return response.body!!["id"] as String
    }

    @Test
    fun `create then radius search through PostGIS, with outbox row in the same transaction`() {
        val near = create("Koramangala Brew", 12.9352, 77.6245)
        create("Whitefield Roasters", 12.9698, 77.7500) // ~14 km away
        create("Specialty Corner", 12.9360, 77.6250, category = "food.cafe.specialty")

        val result = http.getForEntity(
            "/v1/listings?lat=12.9352&lng=77.6245&radius=3000&category=food.cafe&freshness=NEW",
            Map::class.java,
        )
        @Suppress("UNCHECKED_CAST")
        val items = result.body!!["items"] as List<Map<String, Any>>
        assertThat(items.map { it["title"] }).containsExactly("Koramangala Brew", "Specialty Corner")
        assertThat(items.first()["id"]).isEqualTo(near)

        val outboxCount = jdbc.sql("SELECT count(*) FROM outbox WHERE event_type = 'dev.pulse.catalog.listing.published.v1'")
            .query(Long::class.java).single()
        assertThat(outboxCount).isEqualTo(3L)
    }

    @Test
    fun `full-text search over localized titles`() {
        create("Rameshwaram Cafe Dosa Point", 12.9352, 77.6245, category = "food.restaurant.south_indian")
        val result = http.getForEntity("/v1/listings?lat=12.9352&lng=77.6245&q=dosa", Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val items = result.body!!["items"] as List<Map<String, Any>>
        assertThat(items.map { it["title"] }).contains("Rameshwaram Cafe Dosa Point")
    }
}
