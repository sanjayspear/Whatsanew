package dev.pulse.core.data.osm

import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ListingItem
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fetches real places around a point from OpenStreetMap via the Overpass API.
 *
 * Prototype path: the phone calls public Overpass servers (free, rate-limited; identifies itself as
 * the usage policy requires). Production path: catalog-service imports an OSM extract per region
 * and the app calls our gateway instead, with the same [ListingItem] output, so only the URL changes.
 * Data (c) OpenStreetMap contributors, ODbL.
 */
class OverpassClient(
    private val http: HttpClient,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
    private val userAgent: String = "Pulse/0.2 (open-source discovery app; contact via github.com/sanjayspear)",
) {
    suspend fun nearby(center: GeoCoordinate, radiusMeters: Int, categories: List<String>, limit: Int = 150): List<ListingItem> {
        val filters = OsmCategories.filtersFor(categories)
        if (filters.isEmpty()) return emptyList()
        val query = buildQuery(center, radiusMeters, filters, limit)
        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            repeat(2) { attempt ->
                try {
                    val response = http.submitForm(endpoint, Parameters.build { append("data", query) }) {
                        header("User-Agent", userAgent)
                    }
                    val body = response.bodyAsText()
                    if (response.status.isSuccess() && body.trimStart().startsWith("{")) return parse(body)
                    lastError = IllegalStateException("${response.status.value} from ${endpoint.substringAfter("//").substringBefore('/')}")
                    // 429 (rate limit) / 504 (busy): one short retry, then the next mirror.
                    if (response.status.value != 429 && response.status.value != 504) return@repeat
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    lastError = e
                }
                if (attempt == 0) delay(2_000)
            }
        }
        throw IllegalStateException("all OpenStreetMap servers failed", lastError)
    }

    companion object {
        val DEFAULT_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        )

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * One clause per exact tag value (`[amenity=theatre]`), nodes + ways only. Exact values hit
         * Overpass's tag index; regex values (`~"^(a|b)$"`) force a scan and timed out on every public
         * server for an "events in Jayanagar" query, while exact values returned 150 venues in ~5 s.
         */
        fun buildQuery(center: GeoCoordinate, radiusMeters: Int, filters: List<Pair<String, OsmCategories.TagFilter>>, limit: Int): String {
            val around = "around:$radiusMeters,${center.lat},${center.lng}"
            val clauses = filters.map { it.second }
                .flatMap { f -> f.values.map { v -> Triple(f.key, v, f.extra) } }
                .distinct()
                .joinToString("") { (key, value, extra) ->
                    val extraFilter = extra?.let { (k, v) -> "[\"$k\"~\"$v\"]" } ?: ""
                    "nw[\"$key\"=\"$value\"]$extraFilter[\"name\"]($around);"
                }
            return "[out:json][timeout:20];($clauses);out center meta $limit;"
        }

        @Serializable
        private data class Response(val elements: List<Element> = emptyList())

        @Serializable
        private data class Element(
            val type: String,
            val id: Long,
            val lat: Double? = null,
            val lon: Double? = null,
            val center: Center? = null,
            val tags: Map<String, String> = emptyMap(),
            val timestamp: String? = null,
            val version: Int? = null,
        )

        @Serializable
        private data class Center(val lat: Double, val lon: Double)

        fun parse(body: String): List<ListingItem> = json.decodeFromString(Response.serializer(), body).elements.mapNotNull { e ->
            val lat = e.lat ?: e.center?.lat ?: return@mapNotNull null
            val lng = e.lon ?: e.center?.lon ?: return@mapNotNull null
            val name = e.tags["name:en"] ?: e.tags["name"] ?: return@mapNotNull null
            val category = OsmCategories.categoryOf(e.tags) ?: return@mapNotNull null
            val titles = buildMap {
                put("en", name)
                e.tags["name:kn"]?.let { put("kn", it) }
                e.tags["name:hi"]?.let { put("hi", it) }
            }
            // "New" = first mapped recently: for version-1 elements the edit timestamp is the creation time.
            val created = if (e.version == 1) e.timestamp?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } else null
            val tagline = listOfNotNull(
                e.tags["cuisine"]?.replace(';', ',')?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
                e.tags["description"]?.take(80),
                e.tags["stars"]?.let { "$it-star" },
            ).firstOrNull()
            ListingItem(
                id = "osm:${e.type}/${e.id}",
                titles = titles,
                category = category,
                lat = lat,
                lng = lng,
                regionCode = e.tags["addr:state"]?.let { "IN" } ?: "OSM",
                timeZone = "Asia/Kolkata",
                address = listOfNotNull(e.tags["addr:street"], e.tags["addr:suburb"] ?: e.tags["addr:city"]).joinToString(", ").ifEmpty { null },
                phone = (e.tags["phone"] ?: e.tags["contact:phone"])?.split(';')?.firstOrNull()?.replace(" ", "")?.takeIf { it.startsWith("+") },
                website = e.tags["website"] ?: e.tags["contact:website"],
                firstListedAtEpochMs = created ?: 0L,
                tagline = tagline?.let { mapOf("en" to it) }.orEmpty(),
            )
        }
    }
}
