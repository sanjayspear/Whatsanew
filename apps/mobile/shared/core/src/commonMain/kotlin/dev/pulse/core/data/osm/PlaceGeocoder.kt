package dev.pulse.core.data.osm

import dev.pulse.core.data.GeoCoordinate
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resolves area names that are not in the offline place pack ("Mysuru Palace", "Andheri West") via
 * OpenStreetMap Nominatim. Results are remembered, so each name costs one request. The pack covers
 * Bengaluru; this is what makes any other city work too.
 */
class PlaceGeocoder(
    private val http: HttpClient,
    private val endpoint: String = "https://nominatim.openstreetmap.org/search",
    private val userAgent: String = "Pulse/0.2 (open-source discovery app; contact via github.com/sanjayspear)",
) {
    private val cache = mutableMapOf<String, GeoCoordinate?>()

    /** Cached lookup only (synchronous, for rendering). */
    fun known(name: String): GeoCoordinate? = cache[name.lowercase()]

    /** Network lookup, biased towards [near] so "Jayanagar" prefers the Bengaluru one. */
    suspend fun resolve(name: String, near: GeoCoordinate?): GeoCoordinate? {
        val key = name.lowercase()
        if (cache.containsKey(key)) return cache[key]
        val result = runCatching {
            val response = http.get(endpoint) {
                parameter("q", name)
                parameter("format", "jsonv2")
                parameter("limit", 1)
                near?.let { parameter("viewbox", "${it.lng - 0.5},${it.lat + 0.5},${it.lng + 0.5},${it.lat - 0.5}") }
                header("User-Agent", userAgent)
            }
            if (!response.status.isSuccess()) return@runCatching null
            json.decodeFromString<List<Hit>>(response.bodyAsText()).firstOrNull()
                ?.let { GeoCoordinate(it.lat.toDouble(), it.lon.toDouble()) }
        }.getOrNull()
        if (result != null) cache[key] = result // don't cache failures; retry later
        return result
    }

    @Serializable
    private data class Hit(val lat: String, val lon: String)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
