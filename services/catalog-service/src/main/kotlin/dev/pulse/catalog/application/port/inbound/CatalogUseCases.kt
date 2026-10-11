package dev.pulse.catalog.application.port.inbound

import dev.pulse.catalog.domain.GeoPoint
import dev.pulse.catalog.domain.Listing
import dev.pulse.catalog.domain.ListingDraft
import dev.pulse.catalog.domain.ListingId
import dev.pulse.catalog.domain.SearchPage
import java.time.Instant

// Inbound ports: what the outside world (REST today, gRPC tomorrow) may ask of the catalog.
// One interface per use case (ISP), so adapters depend only on what they call.

fun interface PublishListing {
    fun publish(draft: ListingDraft): Listing
}

fun interface ArchiveListing {
    fun archive(id: ListingId): Listing
}

fun interface GetListing {
    fun get(id: ListingId): Listing
}

fun interface SearchListings {
    fun search(query: SearchQuery): SearchPage
}

/** Transport-neutral search input; the service turns it into validated domain criteria. */
data class SearchQuery(
    val center: GeoPoint,
    val radiusMeters: Int? = null,
    val categories: List<String> = emptyList(),
    val text: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val freshness: Freshness? = null,
    val priceLevelMax: Int? = null,
    val limit: Int? = null,
    val cursor: String? = null,
) {
    enum class Freshness { NEW }
}
