package dev.pulse.core.data

import dev.pulse.core.data.osm.OverpassClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Offline region packs: every named hotel, restaurant, shop, gym, park and venue in a city,
 * extracted once from OpenStreetMap (tools/osm-pack) and loaded into the on-device cache.
 *
 * Inside a covered region, search is instant, works offline and never touches the overloaded public
 * Overpass servers. Outside, [NearbySync] falls back to live queries. In production the same packs are
 * produced by catalog-service and downloaded per region instead of being bundled.
 */
class RegionPacks(
    private val cache: SqlListingCache,
    private val readPack: suspend (regionCode: String) -> ByteArray?,
    private val nowMs: () -> Long,
) {
    data class Region(val code: String, val south: Double, val west: Double, val north: Double, val east: Double) {
        fun contains(p: GeoCoordinate) = p.lat in south..north && p.lng in west..east
    }

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _loaded = MutableStateFlow<Set<String>>(emptySet())
    val loaded: StateFlow<Set<String>> = _loaded.asStateFlow()

    fun covers(p: GeoCoordinate): Boolean = AVAILABLE.any { it.code in _loaded.value && it.contains(p) }

    /** Loads bundled packs into the cache on first launch (and after a pack update). */
    suspend fun ensureLoaded() {
        for (region in AVAILABLE) {
            if (region.code in _loaded.value) continue
            if (cache.countInRegion(region.code) > MIN_EXPECTED) {
                _loaded.value += region.code
                continue
            }
            _loading.value = true
            try {
                val bytes = readPack(region.code) ?: continue
                val items = OverpassClient.parse(bytes.decodeToString()).map { it.copy(regionCode = region.code) }
                cache.upsert(items, nowMs())
                _loaded.value += region.code
            } finally {
                _loading.value = false
            }
        }
    }

    companion object {
        val AVAILABLE = listOf(Region("IN-KA-BLR", 12.75, 77.35, 13.20, 77.85))
        private const val MIN_EXPECTED = 1_000L
    }
}
