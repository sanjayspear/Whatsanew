package dev.pulse.catalog.domain

import java.nio.ByteBuffer
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Server-side search is deliberately *candidate generation*: geo + filters, ordered by distance.
 * Personal ranking happens on-device (offline-first, private), with personalization-service as
 * an optional re-ranker. The server stays cacheable and cheap at 10x users.
 */
data class SearchCriteria(
    val center: GeoPoint,
    val radiusMeters: Int,
    val categories: Set<CategoryKey> = emptySet(),
    val text: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val listedAfter: Instant? = null,
    val priceLevelMax: Int? = null,
    val limit: Int = DEFAULT_LIMIT,
    val after: SearchCursor? = null,
) {
    init {
        require(radiusMeters in MIN_RADIUS..MAX_RADIUS) { "radius must be $MIN_RADIUS..$MAX_RADIUS m" }
        require(limit in 1..MAX_LIMIT) { "limit must be 1..$MAX_LIMIT" }
        if (from != null && to != null) require(!to.isBefore(from)) { "'to' must not be before 'from'" }
        priceLevelMax?.let { require(it in 1..4) { "priceLevelMax must be 1..4" } }
    }

    companion object {
        const val MIN_RADIUS = 100
        const val MAX_RADIUS = 50_000
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
    }
}

data class SearchHit(val listing: Listing, val distanceMeters: Double)

data class SearchPage(val hits: List<SearchHit>, val next: SearchCursor?)

/** Keyset cursor on (distance, id): stable under concurrent inserts, O(log n) per page. */
data class SearchCursor(val distanceMeters: Double, val id: UUID) {
    fun encode(): String {
        val buf = ByteBuffer.allocate(24)
            .putDouble(distanceMeters)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array())
    }

    companion object {
        fun decode(token: String): SearchCursor {
            val bytes = runCatching { Base64.getUrlDecoder().decode(token) }.getOrNull()
            require(bytes != null && bytes.size == 24) { "invalid cursor" }
            val buf = ByteBuffer.wrap(bytes)
            return SearchCursor(buf.double, UUID(buf.long, buf.long))
        }
    }
}
