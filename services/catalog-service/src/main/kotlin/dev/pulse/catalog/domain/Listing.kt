package dev.pulse.catalog.domain

import java.time.Instant
import java.time.ZoneId

enum class ListingStatus { PUBLISHED, ARCHIVED }

/**
 * The catalog aggregate. Pure Kotlin with no framework types, so invariants are unit-testable in
 * milliseconds and the persistence technology can change without touching business rules.
 */
data class Listing(
    val id: ListingId,
    val title: LocalizedText,
    val summary: LocalizedText?,
    val category: CategoryKey,
    val location: GeoPoint,
    val region: RegionCode,
    /** The listing's own zone: "this weekend" means the venue's weekend, not the server's. */
    val timeZone: ZoneId,
    val address: String?,
    val priceLevel: Int?,
    val price: Money?,
    val event: EventWindow?,
    val contact: Contact,
    val media: Media,
    val status: ListingStatus,
    val firstListedAt: Instant,
    val updatedAt: Instant,
) {
    init {
        priceLevel?.let { require(it in 1..4) { "priceLevel must be 1..4" } }
    }

    fun archive(now: Instant): Pair<Listing, ListingEvent.Archived> {
        check(status == ListingStatus.PUBLISHED) { "listing $id is already archived" }
        val archived = copy(status = ListingStatus.ARCHIVED, updatedAt = now)
        return archived to ListingEvent.Archived(id, now)
    }

    companion object {
        /** Factory for new listings; publishing always emits [ListingEvent.Published]. */
        fun publish(draft: ListingDraft, id: ListingId, now: Instant): Pair<Listing, ListingEvent.Published> {
            val listing = Listing(
                id = id,
                title = draft.title,
                summary = draft.summary,
                category = draft.category,
                location = draft.location,
                region = draft.region,
                timeZone = draft.timeZone,
                address = draft.address,
                priceLevel = draft.priceLevel,
                price = draft.price,
                event = draft.event,
                contact = draft.contact,
                media = draft.media,
                status = ListingStatus.PUBLISHED,
                firstListedAt = now,
                updatedAt = now,
            )
            return listing to ListingEvent.Published(listing, now)
        }
    }
}

/** Everything the caller supplies; identity, status and timestamps are owned by the domain. */
data class ListingDraft(
    val title: LocalizedText,
    val summary: LocalizedText? = null,
    val category: CategoryKey,
    val location: GeoPoint,
    val region: RegionCode,
    val timeZone: ZoneId,
    val address: String? = null,
    val priceLevel: Int? = null,
    val price: Money? = null,
    val event: EventWindow? = null,
    val contact: Contact = Contact(),
    val media: Media = Media(),
)

sealed interface ListingEvent {
    val listingId: ListingId
    val occurredAt: Instant

    data class Published(val listing: Listing, override val occurredAt: Instant) : ListingEvent {
        override val listingId: ListingId get() = listing.id
    }

    data class Archived(override val listingId: ListingId, override val occurredAt: Instant) : ListingEvent
}

class ListingNotFoundException(id: ListingId) : RuntimeException("listing $id not found")
