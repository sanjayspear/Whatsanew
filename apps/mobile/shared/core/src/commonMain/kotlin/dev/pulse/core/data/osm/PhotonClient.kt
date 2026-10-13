package dev.pulse.core.data.osm

import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ListingItem
import dev.pulse.core.data.haversineMeters
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Search OpenStreetMap by *name* ("Microsoft", "Wells Fargo", "Nexus Mall") through Photon, a free,
 * open-source search engine built on OSM data. Used when someone searches for a specific company,
 * venue or business: the offline pack only covers one city and Overpass only searches by category.
 * Results are biased to the user's location and stored in the offline cache like any other place.
 * Data (c) OpenStreetMap contributors, ODbL.
 */
class PhotonClient(
    private val http: HttpClient,
    private val endpoint: String = "https://photon.komoot.io/api/",
    private val userAgent: String = "SearchAround/0.4 (open-source discovery app; contact via github.com/sanjayspear)",
) {
    suspend fun search(text: String, near: GeoCoordinate, limit: Int = 20, maxDistanceMeters: Double = 60_000.0): List<ListingItem> {
        val response = http.get(endpoint) {
            parameter("q", text)
            parameter("lat", near.lat)
            parameter("lon", near.lng)
            parameter("limit", limit)
            parameter("lang", "en")
            header("User-Agent", userAgent)
        }
        check(response.status.isSuccess()) { "${response.status.value} from photon" }
        return parse(response.bodyAsText()).filter { haversineMeters(near.lat, near.lng, it.lat, it.lng) <= maxDistanceMeters }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class Response(val features: List<Feature> = emptyList())

        @Serializable
        private data class Feature(val geometry: Geometry, val properties: Properties)

        @Serializable
        private data class Geometry(val coordinates: List<Double>)

        @Serializable
        private data class Properties(
            val osm_type: String? = null,
            val osm_id: Long? = null,
            val osm_key: String? = null,
            val osm_value: String? = null,
            val name: String? = null,
            val street: String? = null,
            val district: String? = null,
            val locality: String? = null,
            val city: String? = null,
            val type: String? = null,
        )

        /** Only named points of interest; streets, cities and other areas are not places to go. */
        fun parse(body: String): List<ListingItem> = json.decodeFromString(Response.serializer(), body).features.mapNotNull { f ->
            val p = f.properties
            val name = p.name ?: return@mapNotNull null
            val key = p.osm_key ?: return@mapNotNull null
            val value = p.osm_value ?: return@mapNotNull null
            if (key in NOT_POI || p.type in setOf("street", "city", "district", "county", "state", "country", "locality")) return@mapNotNull null
            val (lng, lat) = f.geometry.coordinates.let { (it.getOrNull(0) ?: return@mapNotNull null) to (it.getOrNull(1) ?: return@mapNotNull null) }
            val category = OsmCategories.categoryOf(mapOf(key to value, "name" to name))
                ?: if (key == "office") "business.office" else "places"
            val type = when (p.osm_type) { "N" -> "node"; "W" -> "way"; "R" -> "relation"; else -> "node" }
            ListingItem(
                id = "osm:$type/${p.osm_id ?: return@mapNotNull null}",
                titles = mapOf("en" to name),
                category = category,
                lat = lat,
                lng = lng,
                regionCode = "OSM",
                timeZone = "Asia/Kolkata",
                address = listOfNotNull(p.street, p.locality ?: p.district, p.city).distinct().joinToString(", ").ifEmpty { null },
                firstListedAtEpochMs = 0L,
                tagline = mapOf("en" to value.replace('_', ' ').replaceFirstChar { it.uppercase() }),
            )
        }

        private val NOT_POI = setOf("highway", "place", "boundary", "railway", "waterway", "natural", "landuse", "route")
    }
}
