package dev.pulse.core.data

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
) {
    fun title(languageTag: String): String = titles.resolve(languageTag)
    fun taglineFor(languageTag: String): String? = tagline.takeIf { it.isNotEmpty() }?.resolve(languageTag)

    /** For `$item.<field>` references in DSL action args. */
    fun field(name: String): String? = when (name) {
        "id" -> id
        "phone" -> phone
        "lat" -> lat.toString()
        "lng" -> lng.toString()
        "videoId" -> videoId
        "website" -> website
        "title" -> titles.resolve("en")
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
