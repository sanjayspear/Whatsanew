package dev.pulse.core.data

import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.prefs.ProfileStore
import dev.pulse.core.signals.Signal
import dev.pulse.core.signals.SignalLog
import dev.pulse.dsl.LayoutPrefs
import dev.pulse.dsl.PulseJson
import dev.pulse.dsl.ThemeSpec

/** SQLDelight-backed adapters for the core ports. Same code on Android, iOS and desktop. */
/** Offline cache of fetched places (any region). Newest first, capped so resolution stays fast. */
class SqlListingCache(private val db: PulseDatabase, private val maxRows: Long = 5_000) : ListingSource {
    override fun all(): List<ListingItem> = decode(db.pulseQueries.recentListings(maxRows).executeAsList())

    /** Indexed bounding-box query (the resolver then applies the exact great-circle radius). */
    override fun near(center: GeoCoordinate, radiusMeters: Int): List<ListingItem> {
        val dLat = radiusMeters / 111_320.0
        val dLng = radiusMeters / (111_320.0 * kotlin.math.cos(center.lat * kotlin.math.PI / 180).coerceAtLeast(0.01))
        return decode(
            db.pulseQueries.listingsInBox(center.lat - dLat, center.lat + dLat, center.lng - dLng, center.lng + dLng, maxRows)
                .executeAsList(),
        )
    }

    /** Filters on light columns first, then loads JSON only for the nearest [max] matches: no row cap. */
    override fun nearMatching(center: GeoCoordinate, radiusMeters: Int, max: Int, keep: (String, String) -> Boolean): List<ListingItem> {
        val (dLat, dLng) = box(center, radiusMeters)
        val ids = db.pulseQueries.candidatesInBox(center.lat - dLat, center.lat + dLat, center.lng - dLng, center.lng + dLng).executeAsList()
            .asSequence()
            .filter { keep(it.category, it.search_text) }
            .map { it.id to haversineMeters(center.lat, center.lng, it.lat, it.lng) }
            .filter { (_, d) -> d <= radiusMeters }
            .sortedBy { it.second }
            .take(max)
            .map { it.first }
            .toList()
        return ids.chunked(500).flatMap { decode(db.pulseQueries.listingsByIds(it).executeAsList()) }
    }

    /** Search texts around a point (for "is this a real name?"), without loading JSON. */
    fun searchTextsNear(center: GeoCoordinate, radiusMeters: Int): List<String> {
        val (dLat, dLng) = box(center, radiusMeters)
        return db.pulseQueries.candidatesInBox(center.lat - dLat, center.lat + dLat, center.lng - dLng, center.lng + dLng)
            .executeAsList().map { it.search_text }
    }

    /** Fills search_text for rows written before migration 2 (runs at start-up, in batches). */
    fun backfillSearchText() {
        while (true) {
            val rows = db.pulseQueries.untitledListings(1_000).executeAsList()
            if (rows.isEmpty()) return
            db.transaction {
                rows.forEach { row ->
                    val text = runCatching { PulseJson.decodeFromString(ListingItem.serializer(), row.json).searchText() }.getOrNull()
                    db.pulseQueries.setSearchText(text?.ifEmpty { " " } ?: " ", row.id)
                }
            }
        }
    }

    private fun box(center: GeoCoordinate, radiusMeters: Int): Pair<Double, Double> {
        val dLat = radiusMeters / 111_320.0
        val dLng = radiusMeters / (111_320.0 * kotlin.math.cos(center.lat * kotlin.math.PI / 180).coerceAtLeast(0.01))
        return dLat to dLng
    }

    fun countInRegion(regionCode: String): Long = db.pulseQueries.countInRegion(regionCode).executeAsOne()

    private fun decode(rows: List<String>) =
        rows.mapNotNull { runCatching { PulseJson.decodeFromString(ListingItem.serializer(), it) }.getOrNull() }

    fun evictOlderThan(epochMs: Long) {
        db.pulseQueries.evictListingsOlderThan(epochMs)
    }

    fun upsert(items: List<ListingItem>, nowMs: Long) = db.transaction {
        items.forEach { item ->
            db.pulseQueries.upsertListing(
                item.id, item.regionCode, item.category, item.lat, item.lng,
                PulseJson.encodeToString(ListingItem.serializer(), item), nowMs, item.searchText(),
            )
        }
    }
}

class SqlSignalLog(private val db: PulseDatabase) : SignalLog {
    override fun append(signal: Signal) {
        db.pulseQueries.insertSignal(PulseJson.encodeToString(Signal.serializer(), signal), signal.atEpochMs)
    }

    override fun since(epochMs: Long): List<Signal> = db.pulseQueries.signalsSince(epochMs).executeAsList()
        .map { PulseJson.decodeFromString(Signal.serializer(), it) }

    override fun pendingUpload(limit: Int): List<Pair<Long, Signal>> = db.pulseQueries.pendingSignals(limit.toLong()).executeAsList()
        .map { it.id to PulseJson.decodeFromString(Signal.serializer(), it.json) }

    override fun markUploaded(ids: List<Long>) {
        db.pulseQueries.markUploaded(ids)
    }
}

class SqlProfileStore(private val db: PulseDatabase) : ProfileStore {
    override fun theme(): ThemeSpec = read(THEME, ThemeSpec.serializer()) ?: ThemeSpec()
    override fun saveTheme(theme: ThemeSpec) { write(THEME, PulseJson.encodeToString(ThemeSpec.serializer(), theme)) }
    override fun layout(): LayoutPrefs = read(LAYOUT, LayoutPrefs.serializer()) ?: LayoutPrefs()
    override fun saveLayout(prefs: LayoutPrefs) { write(LAYOUT, PulseJson.encodeToString(LayoutPrefs.serializer(), prefs)) }

    private fun <T> read(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        db.pulseQueries.getValue(key).executeAsOneOrNull()?.let { runCatching { PulseJson.decodeFromString(serializer, it) }.getOrNull() }

    private fun write(key: String, value: String) {
        db.pulseQueries.putValue(key, value)
    }

    private companion object {
        const val THEME = "profile.theme"
        const val LAYOUT = "profile.layout"
    }
}
