package dev.pulse.core.data

import dev.pulse.core.data.osm.OverpassClient
import dev.pulse.core.data.osm.PhotonClient
import dev.pulse.core.data.osm.PlaceGeocoder
import dev.pulse.dsl.Binding
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.Surface
import dev.pulse.dsl.walk
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the local cache filled with real places for whatever is on screen, around wherever that is.
 *
 * Triggered by every new surface and every > 250 m move. Each (area ~1 km cell, radius, categories)
 * is fetched at most once per [freshForMs]; results go into the offline cache, so the UI always
 * renders from local data and keeps working without network. [version] bumps when new data lands,
 * which makes data-bound components re-resolve.
 */
class NearbySync(
    private val overpass: OverpassClient,
    private val geocoder: PlaceGeocoder,
    private val store: (List<ListingItem>) -> Unit,
    private val location: LocationProvider,
    private val nowMs: () -> Long,
    private val freshForMs: Long = 6 * 60 * 60_000L,
    /** True where an offline region pack already has the data; no live query is needed there. */
    private val coveredOffline: (GeoCoordinate) -> Boolean = { false },
    /** Name search ("Microsoft", "Wells Fargo"); runs everywhere, since no pack has every business. */
    private val names: PhotonClient? = null,
) {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    /** Technical cause of the last failure, for the diagnostics line (not shown to end users). */
    private val _lastErrorDetail = MutableStateFlow<String?>(null)
    val lastErrorDetail: StateFlow<String?> = _lastErrorDetail.asStateFlow()

    private val fetchedAt = mutableMapOf<String, Long>()
    private val lock = Mutex()

    suspend fun syncFor(surface: Surface?) {
        val bindings = surface?.root?.walk()?.mapNotNull { it.bind }?.distinctBy { it.query.near to it.query.categories }?.toList().orEmpty()
        for (binding in bindings) ensure(binding)
    }

    private var pending = 0

    /** Shows "looking…" from the moment a fetch is requested, including while it waits its turn. */
    suspend fun ensure(binding: Binding) {
        pending++
        _syncing.value = true
        try {
            ensureLocked(binding)
        } finally {
            pending--
            _syncing.value = pending > 0
        }
    }

    private suspend fun ensureLocked(binding: Binding) = lock.withLock {
        val query = binding.query
        val center = anchor(query.near?.anchor) ?: return@withLock
        query.text?.takeIf { names != null && it.length >= 2 }?.let { text -> searchByName(text, center) }
        if (coveredOffline(center)) return@withLock // instant and offline from the region pack
        val radius = (query.near?.radiusMeters ?: GeoFilter.DEFAULT_RADIUS_METERS).coerceAtMost(MAX_FETCH_RADIUS)
        val key = "${(center.lat * 100).roundToInt()},${(center.lng * 100).roundToInt()}|$radius|${query.categories.sorted()}"
        val last = fetchedAt[key]
        if (last != null && nowMs() - last < freshForMs) return@withLock
        try {
            store(overpass.nearby(center, radius, query.categories))
            fetchedAt[key] = nowMs()
            _lastError.value = null
            _lastErrorDetail.value = null
            _version.value++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _lastError.value = "Couldn't reach OpenStreetMap. Showing saved places."
            _lastErrorDetail.value = generateSequence(e) { it.cause }.take(3)
                .joinToString(" ← ") { "${it::class.simpleName}: ${it.message?.take(80)}" }
        }
    }

    /** One request per (name, ~10 km area) per [freshForMs]; results join the offline cache. */
    private suspend fun searchByName(text: String, center: GeoCoordinate) {
        val key = "name|${text.lowercase()}|${(center.lat * 10).roundToInt()},${(center.lng * 10).roundToInt()}"
        val last = fetchedAt[key]
        if (last != null && nowMs() - last < freshForMs) return
        try {
            val found = names!!.search(text, center)
            store(found)
            fetchedAt[key] = nowMs()
            if (found.isNotEmpty()) _version.value++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _lastErrorDetail.value = "name search: ${e::class.simpleName}: ${e.message?.take(80)}"
        }
    }

    private suspend fun anchor(anchor: GeoAnchor?): GeoCoordinate? = when (anchor) {
        is GeoAnchor.Point -> GeoCoordinate(anchor.lat, anchor.lng)
        is GeoAnchor.Place -> geocodedOrPack(anchor.name)
        GeoAnchor.Here, null -> location.lastKnown()
    }

    private suspend fun geocodedOrPack(name: String): GeoCoordinate? =
        Gazetteer.Bangalore.lookup(name)?.let { GeoCoordinate(it.lat, it.lng) }
            ?: geocoder.resolve(name, location.lastKnown())?.also { _version.value++ }

    private companion object {
        /** Keeps public-server queries light; ranking still respects the user's radius. */
        const val MAX_FETCH_RADIUS = 10_000
    }
}
