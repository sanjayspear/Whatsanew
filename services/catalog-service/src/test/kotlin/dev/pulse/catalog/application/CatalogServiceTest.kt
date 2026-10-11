package dev.pulse.catalog.application

import dev.pulse.catalog.application.port.inbound.SearchQuery
import dev.pulse.catalog.domain.EventWindow
import dev.pulse.catalog.domain.ListingEvent
import dev.pulse.catalog.domain.ListingId
import dev.pulse.catalog.domain.ListingNotFoundException
import dev.pulse.catalog.domain.ListingStatus
import dev.pulse.catalog.support.FIXED_NOW
import dev.pulse.catalog.support.Places
import dev.pulse.catalog.support.TestCatalog
import dev.pulse.catalog.support.draft
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CatalogServiceTest {

    private val catalog = TestCatalog()
    private val service = catalog.service

    @Test
    fun `publish persists listing and records exactly one event`() {
        val listing = service.publish(draft())
        assertThat(catalog.repository.findById(listing.id)).isEqualTo(listing)
        assertThat(catalog.events.events).singleElement().isInstanceOf(ListingEvent.Published::class.java)
    }

    @Test
    fun `archive transitions state and emits archived event`() {
        val listing = service.publish(draft())
        val archived = service.archive(listing.id)
        assertThat(archived.status).isEqualTo(ListingStatus.ARCHIVED)
        assertThat(catalog.events.events.last()).isInstanceOf(ListingEvent.Archived::class.java)
    }

    @Test
    fun `get unknown listing throws not found`() {
        assertThatThrownBy { service.get(ListingId(UUID.randomUUID())) }.isInstanceOf(ListingNotFoundException::class.java)
    }

    @Test
    fun `search - new cafes in Koramangala within 3 km`() {
        service.publish(draft("Koramangala Brew", at = Places.KORAMANGALA))
        service.publish(draft("Indiranagar Roasters", at = Places.INDIRANAGAR)) // ~4.5 km away
        service.publish(draft("Koramangala Bookstore", category = "shopping.books", at = Places.KORAMANGALA))

        val page = service.search(
            SearchQuery(center = Places.KORAMANGALA, radiusMeters = 3_000, categories = listOf("food.cafe"), freshness = SearchQuery.Freshness.NEW),
        )

        assertThat(page.hits.map { it.listing.title.resolve("en") }).containsExactly("Koramangala Brew")
    }

    @Test
    fun `freshness NEW excludes listings older than the newness window`() {
        val oldClock = Clock.fixed(FIXED_NOW.minus(Duration.ofDays(200)), ZoneOffset.UTC)
        val old = TestCatalog(oldClock)
        old.service.publish(draft("Old Cafe"))
        val listings = old.repository.rows.values.toList()

        listings.forEach { catalog.repository.insert(it.copy(id = ListingId(UUID.randomUUID()))) }
        service.publish(draft("New Cafe"))

        val page = service.search(SearchQuery(center = Places.KORAMANGALA, freshness = SearchQuery.Freshness.NEW))
        assertThat(page.hits.map { it.listing.title.resolve("en") }).containsExactly("New Cafe")
    }

    @Test
    fun `search - theatre near Ravindra Kalakshetra this weekend filters by event window`() {
        val saturday = EventWindow(FIXED_NOW.plus(Duration.ofDays(1)), FIXED_NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(3)))
        val nextMonth = EventWindow(FIXED_NOW.plus(Duration.ofDays(30)), FIXED_NOW.plus(Duration.ofDays(30)).plus(Duration.ofHours(3)))
        service.publish(draft("Yayati", category = "arts.theatre", at = Places.RAVINDRA_KALAKSHETRA, event = saturday))
        service.publish(draft("Hamlet", category = "arts.theatre", at = Places.RAVINDRA_KALAKSHETRA, event = nextMonth))

        val page = service.search(
            SearchQuery(
                center = Places.RAVINDRA_KALAKSHETRA,
                categories = listOf("arts"),
                from = FIXED_NOW,
                to = FIXED_NOW.plus(Duration.ofDays(3)),
            ),
        )
        assertThat(page.hits.map { it.listing.title.resolve("en") }).containsExactly("Yayati")
    }

    @Test
    fun `search pages with a stable keyset cursor`() {
        repeat(5) { i -> service.publish(draft("Cafe $i", at = Places.KORAMANGALA.copy(lat = Places.KORAMANGALA.lat + i * 0.001))) }

        val first = service.search(SearchQuery(center = Places.KORAMANGALA, limit = 2))
        val second = service.search(SearchQuery(center = Places.KORAMANGALA, limit = 2, cursor = first.next!!.encode()))
        val third = service.search(SearchQuery(center = Places.KORAMANGALA, limit = 2, cursor = second.next!!.encode()))

        val all = (first.hits + second.hits + third.hits).map { it.listing.title.resolve("en") }
        assertThat(all).containsExactly("Cafe 0", "Cafe 1", "Cafe 2", "Cafe 3", "Cafe 4")
        assertThat(third.next).isNull()
    }

    @Test
    fun `invalid search input is rejected by domain criteria`() {
        assertThatThrownBy { service.search(SearchQuery(center = Places.KORAMANGALA, radiusMeters = 1_000_000)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.search(SearchQuery(center = Places.KORAMANGALA, limit = 500)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
