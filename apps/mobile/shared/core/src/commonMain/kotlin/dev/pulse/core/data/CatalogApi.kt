package dev.pulse.core.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for the gateway's REST BFF (`/v1/listings` is proxied to catalog-service). The
 * server is an *enhancer*: results are merged into the SQLDelight cache, and the UI always renders
 * from the cache, so losing the network never blanks the screen.
 */
class CatalogApi(private val http: HttpClient) {

    @Serializable
    data class LocationDto(val lat: Double, val lng: Double)

    @Serializable
    data class EventDto(val startsAt: String, val endsAt: String)

    @Serializable
    data class ListingDto(
        val id: String,
        val titles: Map<String, String>,
        val category: String,
        val location: LocationDto,
        val regionCode: String,
        val timeZone: String,
        val address: String? = null,
        val priceLevel: Int? = null,
        val phone: String? = null,
        val website: String? = null,
        val videoId: String? = null,
        val event: EventDto? = null,
        val firstListedAt: String,
    )

    @Serializable
    data class SearchResponse(val items: List<ListingDto>, val nextCursor: String? = null)

    suspend fun search(
        lat: Double, lng: Double, radiusMeters: Int, categories: List<String>, languageTag: String,
    ): List<ListingItem> = http.get("v1/listings") {
        parameter("lat", lat)
        parameter("lng", lng)
        parameter("radius", radiusMeters)
        categories.forEach { parameter("category", it) }
        parameter("limit", 50)
        header("Accept-Language", languageTag)
    }.body<SearchResponse>().items.map { it.toItem() }

    private fun ListingDto.toItem() = ListingItem(
        id = id, titles = titles, category = category, lat = location.lat, lng = location.lng,
        regionCode = regionCode, timeZone = timeZone, address = address, priceLevel = priceLevel,
        phone = phone, website = website, videoId = videoId,
        startsAtEpochMs = event?.startsAt?.let { kotlin.time.Instant.parse(it).toEpochMilliseconds() },
        endsAtEpochMs = event?.endsAt?.let { kotlin.time.Instant.parse(it).toEpochMilliseconds() },
        firstListedAtEpochMs = kotlin.time.Instant.parse(firstListedAt).toEpochMilliseconds(),
    )

    companion object {
        /** Shared Ktor config; the engine (OkHttp / Darwin) is chosen per platform. */
        fun HttpClientConfig<*>.pulseDefaults(baseUrl: String, tokenProvider: () -> String?) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
            install(ContentEncoding) { gzip() }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
            }
            defaultRequest {
                url(baseUrl)
                tokenProvider()?.let { header("Authorization", "DPoP $it") }
            }
        }
    }
}
