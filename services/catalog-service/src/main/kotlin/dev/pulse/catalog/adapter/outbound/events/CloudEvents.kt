package dev.pulse.catalog.adapter.outbound.events

import dev.pulse.catalog.domain.ListingEvent
import java.time.Instant
import java.util.UUID

/**
 * CloudEvents 1.0 structured-mode envelope. Schemas live in
 * `packages/event-contracts/catalog/` and are versioned in the `type` (`.v1`), so a v2 can run
 * side by side during migrations.
 */
data class CloudEvent(
    val specversion: String = "1.0",
    val id: UUID,
    val source: String,
    val type: String,
    val subject: String,
    val time: Instant,
    val datacontenttype: String = "application/json",
    val data: Map<String, Any?>,
)

object CloudEvents {
    const val TOPIC = "catalog.listing.v1"
    private const val SOURCE = "/services/catalog-service"

    fun envelope(event: ListingEvent, eventId: UUID): CloudEvent = when (event) {
        is ListingEvent.Published -> CloudEvent(
            id = eventId,
            source = SOURCE,
            type = "dev.pulse.catalog.listing.published.v1",
            subject = event.listingId.toString(),
            time = event.occurredAt,
            data = with(event.listing) {
                mapOf(
                    "listingId" to id.toString(),
                    "title" to title.values,
                    "category" to category.value,
                    "location" to mapOf("lat" to location.lat, "lng" to location.lng),
                    "regionCode" to region.value,
                    "timeZone" to timeZone.id,
                    "priceLevel" to priceLevel,
                    "startsAt" to this.event?.startsAt,
                    "endsAt" to this.event?.endsAt,
                    "hasVideo" to (media.videoId != null),
                )
            },
        )
        is ListingEvent.Archived -> CloudEvent(
            id = eventId,
            source = SOURCE,
            type = "dev.pulse.catalog.listing.archived.v1",
            subject = event.listingId.toString(),
            time = event.occurredAt,
            data = mapOf("listingId" to event.listingId.toString()),
        )
    }
}
