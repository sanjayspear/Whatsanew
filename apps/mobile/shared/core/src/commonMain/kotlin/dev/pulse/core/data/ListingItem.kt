package dev.pulse.core.data

import dev.pulse.core.data.booking.BookingDestinations
import kotlinx.serialization.Serializable

/** Client-side listing (offline cache row + API DTO). Mirrors catalog-service's Listing schema. */
@Serializable
data class ListingItem(
    val id: String,
    val titles: Map<String, String>,
    val category: String,
    val lat: Double,
    val lng: Double,
    val regionCode: String,
    val timeZone: String,
    val address: String? = null,
    val priceLevel: Int? = null,
    val priceMinor: Long? = null,
    val currency: String? = null,
    val startsAtEpochMs: Long? = null,
    val endsAtEpochMs: Long? = null,
    val phone: String? = null,
    val website: String? = null,
    val videoId: String? = null,
    val firstListedAtEpochMs: Long,
    val opensAtMinute: Int? = null,
    val closesAtMinute: Int? = null,
    val rating: Float? = null,
    val tagline: Map<String, String> = emptyMap(),
    /** A merchant's own booking / ticket link (https only, validated when posted). */
    val bookingUrl: String? = null,
    /** Key of a merchant-uploaded image in [dev.pulse.core.prefs.KeyValueStore], if any. */
    val imageKey: String? = null,
    /** When a merchant offering launches or an event starts (shown on "Recently launched" cards). */
    val launchAtEpochMs: Long? = null,
) {
    fun title(languageTag: String): String = titles.resolve(languageTag)
    fun taglineFor(languageTag: String): String? = tagline.takeIf { it.isNotEmpty() }?.resolve(languageTag)

    /** Lower-case names (all languages) and tagline: what name searches look at. */
    fun searchText(): String = (titles.values + tagline.values).joinToString(" ").lowercase()

    /** For `$item.<field>` references in DSL action args. */
    fun field(name: String): String? = when (name) {
        "id" -> id
        "phone" -> phone
        "lat" -> lat.toString()
        "lng" -> lng.toString()
        "videoId" -> videoId
        "website" -> website
        "title" -> titles.resolve("en")
        // Non-null only when there is somewhere real to book (see BookingDestinations).
        "bookable" -> id.takeIf { BookingDestinations.isBookable(this) }
        else -> null
    }
}

/** A listing placed relative to the query anchor, ready for ranking and rendering. */
data class ResolvedItem(
    val item: ListingItem,
    val distanceMeters: Double,
    val score: Double = 0.0,
)

private fun Map<String, String>.resolve(tag: String): String =
    this[tag] ?: this[tag.substringBefore('-')] ?: this["en"] ?: values.first()
