package dev.pulse.catalog.application.service

import dev.pulse.catalog.application.port.inbound.ArchiveListing
import dev.pulse.catalog.application.port.inbound.GetListing
import dev.pulse.catalog.application.port.inbound.PublishListing
import dev.pulse.catalog.application.port.inbound.SearchListings
import dev.pulse.catalog.application.port.inbound.SearchQuery
import dev.pulse.catalog.application.port.outbound.IdGenerator
import dev.pulse.catalog.application.port.outbound.ListingEventPublisher
import dev.pulse.catalog.application.port.outbound.ListingRepository
import dev.pulse.catalog.application.port.outbound.TransactionRunner
import dev.pulse.catalog.domain.CategoryKey
import dev.pulse.catalog.domain.Listing
import dev.pulse.catalog.domain.ListingDraft
import dev.pulse.catalog.domain.ListingId
import dev.pulse.catalog.domain.ListingNotFoundException
import dev.pulse.catalog.domain.SearchCriteria
import dev.pulse.catalog.domain.SearchCursor
import dev.pulse.catalog.domain.SearchPage
import java.time.Clock
import java.time.Duration

class CatalogService(
    private val listings: ListingRepository,
    private val events: ListingEventPublisher,
    private val tx: TransactionRunner,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val policy: SearchPolicy = SearchPolicy(),
) : PublishListing, ArchiveListing, GetListing, SearchListings {

    /** Tunables that product may change without code changes to the domain. */
    data class SearchPolicy(
        val defaultRadiusMeters: Int = 5_000,
        val newnessWindow: Duration = Duration.ofDays(90),
    )

    override fun publish(draft: ListingDraft): Listing = tx.inTransaction {
        val (listing, event) = Listing.publish(draft, ids.newListingId(), clock.instant())
        listings.insert(listing)
        events.publish(event)
        listing
    }

    override fun archive(id: ListingId): Listing = tx.inTransaction {
        val current = listings.findById(id) ?: throw ListingNotFoundException(id)
        val (archived, event) = current.archive(clock.instant())
        listings.update(archived)
        events.publish(event)
        archived
    }

    override fun get(id: ListingId): Listing = listings.findById(id) ?: throw ListingNotFoundException(id)

    override fun search(query: SearchQuery): SearchPage {
        val criteria = SearchCriteria(
            center = query.center,
            radiusMeters = query.radiusMeters ?: policy.defaultRadiusMeters,
            categories = query.categories.map(::CategoryKey).toSet(),
            text = query.text?.trim()?.takeIf { it.isNotEmpty() },
            from = query.from,
            to = query.to,
            listedAfter = when (query.freshness) {
                SearchQuery.Freshness.NEW -> clock.instant().minus(policy.newnessWindow)
                null -> null
            },
            priceLevelMax = query.priceLevelMax,
            limit = query.limit ?: SearchCriteria.DEFAULT_LIMIT,
            after = query.cursor?.let(SearchCursor::decode),
        )
        return listings.search(criteria)
    }
}
