package dev.pulse.catalog.adapter.inbound.web

import dev.pulse.catalog.domain.CategoryKey
import dev.pulse.catalog.domain.Contact
import dev.pulse.catalog.domain.EventWindow
import dev.pulse.catalog.domain.GeoPoint
import dev.pulse.catalog.domain.Listing
import dev.pulse.catalog.domain.ListingDraft
import dev.pulse.catalog.domain.LocalizedText
import dev.pulse.catalog.domain.Media
import dev.pulse.catalog.domain.Money
import dev.pulse.catalog.domain.RegionCode
import dev.pulse.catalog.domain.SearchHit
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.Instant
import java.time.ZoneId

// Transport DTOs. They mirror api/openapi.yaml (the contract is the source of truth) and are
// mapped to domain types at the edge, so domain classes never leak Jackson or validation annotations.

data class CreateListingRequest(
    @field:NotEmpty val title: Map<String, String>,
    val summary: Map<String, String>? = null,
    @field:NotBlank val category: String,
    @field:Valid val location: LocationDto,
    @field:NotBlank val regionCode: String,
    @field:NotBlank val timeZone: String,
    val address: String? = null,
    @field:Min(1) @field:Max(4) val priceLevel: Int? = null,
    @field:Valid val price: MoneyDto? = null,
    @field:Valid val event: EventDto? = null,
    val phone: String? = null,
    val website: String? = null,
    val heroImageUrl: String? = null,
    val videoId: String? = null,
) {
    fun toDraft() = ListingDraft(
        title = LocalizedText(title),
        summary = summary?.let(::LocalizedText),
        category = CategoryKey(category),
        location = GeoPoint(location.lat, location.lng),
        region = RegionCode(regionCode),
        timeZone = ZoneId.of(timeZone),
        address = address,
        priceLevel = priceLevel,
        price = price?.let { Money(it.amountMinor, it.currency) },
        event = event?.let { EventWindow(it.startsAt, it.endsAt) },
        contact = Contact(phone, website),
        media = Media(heroImageUrl, videoId),
    )
}

data class LocationDto(
    @field:DecimalMin("-90") @field:DecimalMax("90") val lat: Double,
    @field:DecimalMin("-180") @field:DecimalMax("180") val lng: Double,
)

data class MoneyDto(@field:Min(0) val amountMinor: Long, @field:NotBlank val currency: String)

data class EventDto(val startsAt: Instant, val endsAt: Instant)

data class ListingResponse(
    val id: String,
    /** Resolved for the caller's Accept-Language. */
    val title: String,
    val summary: String?,
    /** All translations, so the offline cache can switch language without a refetch. */
    val titles: Map<String, String>,
    val category: String,
    val location: LocationDto,
    val regionCode: String,
    val timeZone: String,
    val address: String?,
    val priceLevel: Int?,
    val price: MoneyDto?,
    val event: EventDto?,
    val phone: String?,
    val website: String?,
    val heroImageUrl: String?,
    val videoId: String?,
    val status: String,
    val firstListedAt: Instant,
    val updatedAt: Instant,
    val distanceMeters: Double? = null,
) {
    companion object {
        fun from(listing: Listing, language: String?, distanceMeters: Double? = null) = ListingResponse(
            id = listing.id.toString(),
            title = listing.title.resolve(language),
            summary = listing.summary?.resolve(language),
            titles = listing.title.values,
            category = listing.category.value,
            location = LocationDto(listing.location.lat, listing.location.lng),
            regionCode = listing.region.value,
            timeZone = listing.timeZone.id,
            address = listing.address,
            priceLevel = listing.priceLevel,
            price = listing.price?.let { MoneyDto(it.amountMinor, it.currency) },
            event = listing.event?.let { EventDto(it.startsAt, it.endsAt) },
            phone = listing.contact.phoneE164,
            website = listing.contact.website,
            heroImageUrl = listing.media.heroImageUrl,
            videoId = listing.media.videoId,
            status = listing.status.name,
            firstListedAt = listing.firstListedAt,
            updatedAt = listing.updatedAt,
            distanceMeters = distanceMeters,
        )

        fun from(hit: SearchHit, language: String?) = from(hit.listing, language, hit.distanceMeters)
    }
}

data class SearchResponse(val items: List<ListingResponse>, val nextCursor: String?)
