package dev.pulse.core.data

import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.prefs.ProfileStore
import dev.pulse.core.signals.Signal
import dev.pulse.core.signals.SignalLog
import dev.pulse.dsl.LayoutPrefs
import dev.pulse.dsl.PulseJson
import dev.pulse.dsl.ThemeSpec

/** SQLDelight-backed adapters for the core ports. Same code on Android, iOS and desktop. */
class SqlListingCache(private val db: PulseDatabase, private val regionCode: String) : ListingSource {
    override fun all(): List<ListingItem> = db.pulseQueries.listingsInRegion(regionCode).executeAsList()
        .mapNotNull { runCatching { PulseJson.decodeFromString(ListingItem.serializer(), it) }.getOrNull() }

    fun upsert(items: List<ListingItem>, nowMs: Long) = db.transaction {
        items.forEach { item ->
            db.pulseQueries.upsertListing(
                item.id, item.regionCode, item.category, item.lat, item.lng,
                PulseJson.encodeToString(ListingItem.serializer(), item), nowMs,
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
