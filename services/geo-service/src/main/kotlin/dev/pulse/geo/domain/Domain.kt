package dev.pulse.geo.domain

/**
 * Geohash, the cache/partition key for "listings near X" at the CDN and in Redis. Precision 6
 * is about 1.2 km x 0.6 km cells. It is region-agnostic, so the same code works for Bangalore and Berlin.
 */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lng: Double, precision: Int = 6): String {
        var latR = -90.0 to 90.0
        var lngR = -180.0 to 180.0
        val sb = StringBuilder()
        var bit = 0; var ch = 0; var even = true
        while (sb.length < precision) {
            if (even) {
                val mid = (lngR.first + lngR.second) / 2
                if (lng >= mid) { ch = ch or (1 shl (4 - bit)); lngR = mid to lngR.second } else lngR = lngR.first to mid
            } else {
                val mid = (latR.first + latR.second) / 2
                if (lat >= mid) { ch = ch or (1 shl (4 - bit)); latR = mid to latR.second } else latR = latR.first to mid
            }
            even = !even
            if (bit < 4) bit++ else { sb.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return sb.toString()
    }
}

data class Place(val id: String, val name: String, val lat: Double, val lng: Double, val regionCode: String)

/** Outbound port: spatial store (PostGIS). */
interface PlaceRepository {
    fun within(lat: Double, lng: Double, radiusMeters: Int, limit: Int): List<Place>
    fun gazetteer(regionCode: String): List<Place>
}
