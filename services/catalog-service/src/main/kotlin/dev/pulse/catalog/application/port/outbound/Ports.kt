package dev.pulse.catalog.application.port.outbound

import dev.pulse.catalog.domain.Listing
import dev.pulse.catalog.domain.ListingEvent
import dev.pulse.catalog.domain.ListingId
import dev.pulse.catalog.domain.SearchCriteria
import dev.pulse.catalog.domain.SearchPage

interface ListingRepository {
    fun insert(listing: Listing)
    fun update(listing: Listing)
    fun findById(id: ListingId): Listing?
    fun search(criteria: SearchCriteria): SearchPage
}

/**
 * Records domain events for asynchronous delivery. The production adapter writes to a
 * transactional outbox table in the *same* DB transaction as the listing (no dual-write), and a
 * relay forwards to Kafka.
 */
fun interface ListingEventPublisher {
    fun publish(event: ListingEvent)
}

/** Keeps `@Transactional` (a framework concern) out of the application layer. */
interface TransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}

fun interface IdGenerator {
    fun newListingId(): ListingId
}
