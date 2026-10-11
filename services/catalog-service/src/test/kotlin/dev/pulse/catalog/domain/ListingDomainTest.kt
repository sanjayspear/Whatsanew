package dev.pulse.catalog.domain

import dev.pulse.catalog.support.FIXED_NOW
import dev.pulse.catalog.support.draft
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ListingDomainTest {

    @Nested
    inner class LocalizedTextResolution {
        private val text = LocalizedText(mapOf("en" to "Theatre", "kn" to "ರಂಗಭೂಮಿ", "ta" to "நாடகம்"))

        @Test fun `exact tag wins`() = assertThat(text.resolve("kn")).isEqualTo("ರಂಗಭೂಮಿ")
        @Test fun `region tag falls back to language`() = assertThat(text.resolve("ta-IN")).isEqualTo("நாடகம்")
        @Test fun `unknown language falls back to english`() = assertThat(text.resolve("fr")).isEqualTo("Theatre")
        @Test fun `empty text is rejected`() {
            assertThatThrownBy { LocalizedText(emptyMap()) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class ValueObjects {
        @ParameterizedTest
        @ValueSource(strings = ["Food.Cafe", "food..cafe", "food.", ""])
        fun `malformed categories are rejected`(raw: String) {
            assertThatThrownBy { CategoryKey(raw) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `category prefix matching respects segment boundaries`() {
            val specialty = CategoryKey("food.cafe.specialty")
            assertThat(specialty.isUnder(CategoryKey("food.cafe"))).isTrue()
            assertThat(specialty.isUnder(CategoryKey("food"))).isTrue()
            assertThat(CategoryKey("food.cafeteria").isUnder(CategoryKey("food.cafe"))).isFalse()
        }

        @Test
        fun `money is minor units with ISO currency`() {
            assertThat(Money(49_900, "INR").amountMinor).isEqualTo(49_900)
            assertThatThrownBy { Money(100, "rupees") }.isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { Money(-1, "INR") }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `region codes are global ISO 3166-2 based`() {
            RegionCode("IN-KA-BLR"); RegionCode("US-NY"); RegionCode("GB-LND")
            assertThatThrownBy { RegionCode("bangalore") }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `phone numbers must be E164`() {
            assertThatThrownBy { Contact(phoneE164 = "080 4123 4567") }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `event windows overlap half-open ranges correctly`() {
            val show = EventWindow(Instant.parse("2026-02-14T13:30:00Z"), Instant.parse("2026-02-14T16:00:00Z"))
            assertThat(show.overlaps(Instant.parse("2026-02-14T00:00:00Z"), Instant.parse("2026-02-15T00:00:00Z"))).isTrue()
            assertThat(show.overlaps(Instant.parse("2026-02-15T00:00:00Z"), null)).isFalse()
            assertThatThrownBy { EventWindow(show.endsAt, show.startsAt) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class Lifecycle {
        @Test
        fun `publishing emits a published event`() {
            val (listing, event) = Listing.publish(draft(), ListingId(UUID.randomUUID()), FIXED_NOW)
            assertThat(listing.status).isEqualTo(ListingStatus.PUBLISHED)
            assertThat(listing.firstListedAt).isEqualTo(FIXED_NOW)
            assertThat(event.listing).isEqualTo(listing)
        }

        @Test
        fun `archiving twice is an invalid transition`() {
            val (listing, _) = Listing.publish(draft(), ListingId(UUID.randomUUID()), FIXED_NOW)
            val (archived, _) = listing.archive(FIXED_NOW)
            assertThatThrownBy { archived.archive(FIXED_NOW) }.isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Nested
    inner class Cursors {
        @Test
        fun `cursor round-trips`() {
            val c = SearchCursor(1234.5, UUID.randomUUID())
            assertThat(SearchCursor.decode(c.encode())).isEqualTo(c)
        }

        @Test
        fun `garbage cursor is a client error`() {
            assertThatThrownBy { SearchCursor.decode("not-a-cursor!!") }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
