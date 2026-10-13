package dev.pulse.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A structured, locale-free description of *what content* a surface should show.
 *
 * This is the only thing the AI is allowed to say about content. Listings themselves always
 * come from the data layer (local SQLDelight cache first, server second), so the model can never
 * hallucinate a cafe, a phone number, or a show time.
 */
@Serializable
data class ListingQuery(
    /** Dotted taxonomy keys, e.g. `food.cafe`, `arts.theatre`. Global, never localized. */
    val categories: List<String> = emptyList(),
    /** Free-text residue the parser could not map to structure ("dosa", "rooftop"). */
    val text: String? = null,
    val near: GeoFilter? = null,
    val time: TimeWindow? = null,
    val freshness: Freshness? = null,
    /** 1 (cheap) .. 4 (premium); currency-agnostic on purpose. */
    val priceLevelMax: Int? = null,
    val openNow: Boolean? = null,
) {
    /** Field-wise merge used by REFINE intents: `other` wins where it says something. */
    fun refinedBy(other: ListingQuery): ListingQuery = ListingQuery(
        categories = other.categories.ifEmpty { categories },
        text = other.text ?: text,
        near = other.near ?: near,
        time = other.time ?: time,
        freshness = other.freshness ?: freshness,
        priceLevelMax = other.priceLevelMax ?: priceLevelMax,
        openNow = other.openNow ?: openNow,
    )
}

@Serializable
data class GeoFilter(
    val anchor: GeoAnchor,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 6_000
        const val MIN_RADIUS_METERS = 100
        const val MAX_RADIUS_METERS = 50_000
    }
}

/** Where a geo filter is centred. Named places are resolved on-device by the gazetteer. */
@Serializable
sealed interface GeoAnchor {
    @Serializable
    @SerialName("here")
    data object Here : GeoAnchor

    @Serializable
    @SerialName("place")
    data class Place(val name: String, val placeId: String? = null) : GeoAnchor

    @Serializable
    @SerialName("point")
    data class Point(val lat: Double, val lng: Double) : GeoAnchor
}

/**
 * Relative presets are kept symbolic in the DSL ("this weekend"), and resolved to absolute
 * instants by the data layer in the *listing's* time zone. That keeps the DSL global-ready.
 */
@Serializable
data class TimeWindow(
    val preset: TimePreset? = null,
    val fromEpochMs: Long? = null,
    val toEpochMs: Long? = null,
)

@Serializable
enum class TimePreset { NOW, TODAY, TONIGHT, TOMORROW, THIS_WEEKEND, NEXT_7_DAYS }

@Serializable
enum class Freshness {
    /** Opened / first listed recently (default: last 90 days). */
    NEW,
    /** Trending in the last 7 days. */
    TRENDING,
}

@Serializable
data class RankSpec(
    val strategy: RankStrategy = RankStrategy.PERSONAL,
)

@Serializable
enum class RankStrategy {
    /** On-device personal ranker (affinity x distance x freshness). The default. */
    PERSONAL,
    DISTANCE,
    SOONEST,
    NEWEST,
    POPULAR,
}
