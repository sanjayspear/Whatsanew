package dev.pulse.catalog.support

import dev.pulse.catalog.application.port.outbound.IdGenerator
import dev.pulse.catalog.application.port.outbound.ListingEventPublisher
import dev.pulse.catalog.application.port.outbound.ListingRepository
import dev.pulse.catalog.application.port.outbound.TransactionRunner
import dev.pulse.catalog.application.service.CatalogService
import dev.pulse.catalog.domain.CategoryKey
import dev.pulse.catalog.domain.Contact
import dev.pulse.catalog.domain.EventWindow
import dev.pulse.catalog.domain.GeoPoint
import dev.pulse.catalog.domain.Listing
import dev.pulse.catalog.domain.ListingDraft
import dev.pulse.catalog.domain.ListingEvent
import dev.pulse.catalog.domain.ListingId
import dev.pulse.catalog.domain.ListingStatus
import dev.pulse.catalog.domain.LocalizedText
import dev.pulse.catalog.domain.RegionCode
import dev.pulse.catalog.domain.SearchCriteria
import dev.pulse.catalog.domain.SearchCursor
import dev.pulse.catalog.domain.SearchHit
import dev.pulse.catalog.domain.SearchPage
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Places {
    val KORAMANGALA = GeoPoint(12.9352, 77.6245)
    val INDIRANAGAR = GeoPoint(12.9719, 77.6412)
    val RAVINDRA_KALAKSHETRA = GeoPoint(12.9634, 77.5855)
}

fun draft(
    title: String = "Third Wave Coffee",
    category: String = "food.cafe",
    at: GeoPoint = Places.KORAMANGALA,
    event: EventWindow? = null,
    priceLevel: Int? = 2,
) = ListingDraft(
    title = LocalizedText(mapOf("en" to title, "kn" to "$title (ಕನ್ನಡ)")),
    category = CategoryKey(category),
    location = at,
    region = RegionCode("IN-KA-BLR"),
    timeZone = ZoneId.of("Asia/Kolkata"),
    priceLevel = priceLevel,
    event = event,
    contact = Contact(phoneE164 = "+918041234567"),
)

/** In-memory adapter that mirrors the SQL semantics, so application tests need no database. */
class InMemoryListingRepository : ListingRepository {
    val rows = linkedMapOf<ListingId, Listing>()

    override fun insert(listing: Listing) {
        check(rows.putIfAbsent(listing.id, listing) == null) { "duplicate ${listing.id}" }
    }

    override fun update(listing: Listing) {
        check(rows.containsKey(listing.id)); rows[listing.id] = listing
    }

    override fun findById(id: ListingId) = rows[id]

    override fun search(criteria: SearchCriteria): SearchPage {
        val hits = rows.values.asSequence()
            .filter { it.status == ListingStatus.PUBLISHED }
            .map { SearchHit(it, haversineMeters(criteria.center, it.location)) }
            .filter { it.distanceMeters <= criteria.radiusMeters }
            .filter { h -> criteria.categories.isEmpty() || criteria.categories.any { h.listing.category.isUnder(it) } }
            .filter { h -> criteria.text == null || h.listing.title.values.values.any { it.contains(criteria.text!!, ignoreCase = true) } }
            .filter { h -> h.listing.event?.overlaps(criteria.from, criteria.to) ?: true }
            .filter { h -> criteria.listedAfter == null || !h.listing.firstListedAt.isBefore(criteria.listedAfter) }
            .filter { h -> criteria.priceLevelMax == null || (h.listing.priceLevel ?: 0) <= criteria.priceLevelMax!! }
            .sortedWith(compareBy<SearchHit> { it.distanceMeters }.thenBy { it.listing.id.value })
            .filter { h -> criteria.after == null || compareValuesBy(h, criteria.after!!, { it.first }, { it.second }) > 0 }
            .toList()
        val page = hits.take(criteria.limit)
        val next = if (hits.size > criteria.limit) page.last().let { SearchCursor(it.distanceMeters, it.listing.id.value) } else null
        return SearchPage(page, next)
    }

    private fun compareValuesBy(h: SearchHit, c: SearchCursor, vararg sel: (Pair<Double, UUID>) -> Comparable<*>?): Int =
        kotlin.comparisons.compareValuesBy(h.distanceMeters to h.listing.id.value, c.distanceMeters to c.id, *sel)
}

class RecordingEventPublisher : ListingEventPublisher {
    val events = mutableListOf<ListingEvent>()
    override fun publish(event: ListingEvent) {
        events += event
    }
}

object DirectTransactionRunner : TransactionRunner {
    override fun <T> inTransaction(block: () -> T): T = block()
}

class SequentialIds : IdGenerator {
    private var n = 0L
    override fun newListingId() = ListingId(UUID(0, ++n))
}

val FIXED_NOW: Instant = Instant.parse("2026-02-14T10:00:00Z")

class TestCatalog(clock: Clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)) {
    val repository = InMemoryListingRepository()
    val events = RecordingEventPublisher()
    val service = CatalogService(repository, events, DirectTransactionRunner, SequentialIds(), clock)
}

fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_008.8
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}
