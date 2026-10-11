package dev.pulse.core.data

import dev.pulse.core.signals.LocalRanker
import dev.pulse.dsl.Binding
import dev.pulse.dsl.Freshness
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.RankStrategy
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Anything that can enumerate locally available listings (seed, SQLDelight cache). */
fun interface ListingSource {
    fun all(): List<ListingItem>

    /** Listings around a point. Large stores override this with an indexed spatial query. */
    fun near(center: GeoCoordinate, radiusMeters: Int): List<ListingItem> = all()
}

/**
 * Resolves a DSL [Binding] to ranked items *entirely on-device*: geo, filters, then personal
 * ranking. The same semantics as catalog-service search, so online and offline results agree.
 * At Phase 1 scale (a few thousand cached listings per city) this is a linear scan, far under a
 * millisecond. An R-tree/geohash index slots in behind [ListingSource] if needed.
 */
class ListingResolver(
    private val source: ListingSource,
    private val gazetteer: Gazetteer,
    private val location: LocationProvider,
    private val ranker: LocalRanker,
    private val nowMs: () -> Long,
    /** Coordinates for area names outside the offline pack, learned online (cached). */
    private val geocoded: (String) -> GeoCoordinate? = { null },
) {
    /**
     * Like [resolve], but never a dead end: if fewer than [minResults] places are within the radius,
     * widen it (x2.5, up to [maxRadiusMeters]) and say so. Sparse areas still get the nearest options.
     */
    fun resolveWidening(binding: Binding, minResults: Int = 3, maxRadiusMeters: Int = 10_000): Resolution {
        val requested = binding.query.near?.radiusMeters ?: GeoFilter.DEFAULT_RADIUS_METERS
        var radius = requested
        var items = resolve(binding)
        val withinRequested = items.size
        while (items.size < minResults && radius < maxRadiusMeters && binding.query.near != null) {
            radius = (radius * 5 / 2).coerceAtMost(maxRadiusMeters)
            val wider = binding.copy(query = binding.query.copy(near = binding.query.near!!.copy(radiusMeters = radius)))
            items = resolve(wider)
        }
        return Resolution(items, requestedRadiusMeters = requested, radiusMeters = radius, withinRequested = withinRequested)
    }

    fun resolve(binding: Binding): List<ResolvedItem> {
        val q = binding.query
        val anchor = anchorOf(q.near?.anchor)
        val radius = q.near?.radiusMeters ?: GeoFilter.DEFAULT_RADIUS_METERS
        val now = nowMs()
        val text = q.text?.lowercase()

        val candidates = source.near(anchor, radius).asSequence()
            .map { ResolvedItem(it, haversineMeters(anchor.lat, anchor.lng, it.lat, it.lng)) }
            .filter { it.distanceMeters <= radius }
            .filter { r -> q.categories.isEmpty() || q.categories.any { c -> r.item.category == c || r.item.category.startsWith("$c.") } }
            // Text is a hard filter only when there is no category; otherwise it boosts ranking.
            .filter { r -> text == null || q.categories.isNotEmpty() || r.matchesText(text) }
            .filter { r -> q.time == null || r.item.startsAtEpochMs == null || overlaps(r.item, TimeWindows.resolve(q.time!!, now, r.item.timeZone)) }
            .filter { r -> r.item.endsAtEpochMs == null || r.item.endsAtEpochMs!! >= now }
            .filter { r ->
                when (q.freshness) {
                    Freshness.NEW -> r.item.firstListedAtEpochMs >= now - NEWNESS_WINDOW_MS
                    Freshness.TRENDING -> (r.item.rating ?: 0f) >= 4.5f
                    null -> true
                }
            }
            .filter { r -> q.priceLevelMax == null || (r.item.priceLevel ?: 1) <= q.priceLevelMax!! }
            .filter { r -> q.openNow != true || isOpen(r.item, now) }
            .toList()

        val ranked = when (binding.rank.strategy) {
            RankStrategy.PERSONAL -> candidates
                .map { it.copy(score = ranker.score(it, radius, now) + if (text != null && it.matchesText(text)) 0.25 else 0.0) }
                .sortedByDescending { it.score }
            RankStrategy.DISTANCE -> candidates.sortedBy { it.distanceMeters }
            RankStrategy.SOONEST -> candidates.sortedWith(compareBy(nullsLast()) { it.item.startsAtEpochMs })
            RankStrategy.NEWEST -> candidates.sortedByDescending { it.item.firstListedAtEpochMs }
            RankStrategy.POPULAR -> candidates.sortedByDescending { it.item.rating ?: 0f }
        }
        return ranked.take(binding.limit)
    }

    fun anchorOf(anchor: GeoAnchor?): GeoCoordinate = when (anchor) {
        is GeoAnchor.Point -> GeoCoordinate(anchor.lat, anchor.lng)
        is GeoAnchor.Place -> gazetteer.lookup(anchor.name)?.let { GeoCoordinate(it.lat, it.lng) }
            ?: geocoded(anchor.name)
            ?: location.lastKnown()
        GeoAnchor.Here, null -> location.lastKnown()
    }

    private fun ResolvedItem.matchesText(text: String): Boolean =
        item.titles.values.any { it.lowercase().contains(text) } ||
            item.tagline.values.any { it.lowercase().contains(text) } ||
            item.category.contains(text)

    private fun overlaps(item: ListingItem, range: EpochRange?): Boolean {
        if (range == null) return true
        val start = item.startsAtEpochMs ?: return true
        val end = item.endsAtEpochMs ?: start
        return end >= range.fromMs && start < range.toMs
    }

    private fun isOpen(item: ListingItem, now: Long): Boolean {
        val opens = item.opensAtMinute ?: return item.startsAtEpochMs == null || item.startsAtEpochMs!! <= now + 2 * 60 * 60_000
        val closes = item.closesAtMinute ?: return true
        val local = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.of(item.timeZone))
        val minute = local.hour * 60 + local.minute
        return if (closes > opens) minute in opens until closes else minute >= opens || minute < closes
    }

    private companion object {
        const val NEWNESS_WINDOW_MS = 90L * 24 * 60 * 60_000
    }
}

/** Results plus the radius that produced them (may be wider than requested in sparse areas). */
data class Resolution(
    val items: List<ResolvedItem>,
    val requestedRadiusMeters: Int,
    val radiusMeters: Int,
    /** How many results were inside the radius the user asked for. */
    val withinRequested: Int = items.size,
) {
    val widened: Boolean get() = radiusMeters > requestedRadiusMeters
}

fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_008.8
    val dLat = (lat2 - lat1).toRadians()
    val dLng = (lng2 - lng1).toRadians()
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * asin(sqrt(h))
}

private fun Double.toRadians() = this * kotlin.math.PI / 180.0
