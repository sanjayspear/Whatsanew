package dev.pulse.catalog.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class ListingId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun parse(raw: String): ListingId = ListingId(UUID.fromString(raw))
    }
}

data class GeoPoint(val lat: Double, val lng: Double) {
    init {
        require(lat in -90.0..90.0) { "lat $lat out of range" }
        require(lng in -180.0..180.0) { "lng $lng out of range" }
    }
}

/**
 * Text in several languages keyed by BCP-47 tag (`en`, `kn`, `hi`, `ta-IN`). Global-ready from day
 * one, even though Phase 1 only seeds English and Kannada.
 */
data class LocalizedText(val values: Map<String, String>) {
    init {
        require(values.isNotEmpty()) { "localized text needs at least one language" }
        require(values.values.none { it.isBlank() }) { "localized text values must not be blank" }
    }

    /** Exact tag, then language-only (`ta-IN` -> `ta`), then [fallback], then anything. */
    fun resolve(languageTag: String?, fallback: String = "en"): String {
        val tag = languageTag?.takeIf { it.isNotBlank() }
        return tag?.let { values[it] }
            ?: tag?.substringBefore('-')?.let { values[it] }
            ?: values[fallback]
            ?: values.values.first()
    }

    companion object {
        fun en(text: String) = LocalizedText(mapOf("en" to text))
    }
}

/** Minor units + ISO-4217 code: never floating point, never an implied currency. */
data class Money(val amountMinor: Long, val currency: String) {
    init {
        require(amountMinor >= 0) { "amount must be >= 0" }
        require(CURRENCY.matches(currency)) { "currency '$currency' is not ISO-4217" }
    }

    private companion object {
        val CURRENCY = Regex("^[A-Z]{3}$")
    }
}

/** Dotted taxonomy key shared with the mobile UI DSL, e.g. `food.cafe`, `arts.theatre`. */
@JvmInline
value class CategoryKey(val value: String) {
    init {
        require(PATTERN.matches(value)) { "category '$value' must look like 'food.cafe'" }
    }

    /** `food.cafe.specialty` is under `food.cafe` and `food`, but not under `food.ca`. */
    fun isUnder(prefix: CategoryKey): Boolean = value == prefix.value || value.startsWith(prefix.value + ".")

    private companion object {
        val PATTERN = Regex("^[a-z]+(\\.[a-z_]+)*$")
    }
}

/**
 * ISO 3166-2 subdivision plus a city suffix: `IN-KA-BLR`. It is the partition key for data
 * placement and the unit of the scale roadmap (Bangalore -> Tier-1 India -> global).
 */
@JvmInline
value class RegionCode(val value: String) {
    init {
        require(PATTERN.matches(value)) { "region '$value' must look like 'IN-KA-BLR'" }
    }

    private companion object {
        val PATTERN = Regex("^[A-Z]{2}(-[A-Z0-9]{1,3}){1,2}$")
    }
}

data class EventWindow(val startsAt: Instant, val endsAt: Instant) {
    init {
        require(endsAt.isAfter(startsAt)) { "event must end after it starts" }
    }

    fun overlaps(from: Instant?, to: Instant?): Boolean =
        (from == null || !endsAt.isBefore(from)) && (to == null || !startsAt.isAfter(to))
}

data class Contact(val phoneE164: String? = null, val website: String? = null) {
    init {
        phoneE164?.let { require(E164.matches(it)) { "phone '$it' is not E.164" } }
    }

    private companion object {
        val E164 = Regex("^\\+[1-9][0-9]{6,14}$")
    }
}

data class Media(val heroImageUrl: String? = null, val videoId: String? = null)
