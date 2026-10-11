package dev.pulse.core.data

/**
 * On-device place resolver, so "near Ravindra Kalakshetra" works with zero network. Ships per
 * region as a small signed data pack (Bangalore is ~2k entries / ~150 KB). Coordinates are WGS84
 * and region codes are ISO 3166-2 based, so nothing here is Bangalore-specific except the data.
 */
data class Place(
    val name: String,
    val lat: Double,
    val lng: Double,
    val regionCode: String,
    val aliases: List<String> = emptyList(),
)

class Gazetteer(private val places: List<Place>) {

    private val index: List<Pair<String, Place>> = places
        .flatMap { p -> (p.aliases + p.name).map { it.lowercase() to p } }
        .sortedByDescending { it.first.length }

    /** Longest alias that appears as whole words in [normalizedText]. */
    fun findIn(normalizedText: String): Place? = matchIn(normalizedText)?.second

    /** Like [findIn], also returning the alias that matched (so callers can strip it). */
    fun matchIn(normalizedText: String): Pair<String, Place>? {
        val padded = " $normalizedText "
        return index.firstOrNull { (alias, _) -> padded.contains(" $alias ") }
    }

    fun lookup(name: String): Place? = places.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: findIn(name.lowercase())

    companion object {
        /** Hand-curated landmarks first (they win on name clashes), then the OSM neighbourhood pack. */
        val Bangalore: Gazetteer by lazy {
            val curated = Curated
            val names = curated.flatMap { p -> (p.aliases + p.name).map(String::lowercase) }.toSet()
            Gazetteer(curated + BengaluruOsmPlaces.filterNot { it.name.lowercase() in names })
        }

        private val Curated: List<Place> =
            listOf(
                Place("Koramangala", 12.9352, 77.6245, "IN-KA-BLR", listOf("kormangala", "koramangla")),
                Place("Indiranagar", 12.9719, 77.6412, "IN-KA-BLR", listOf("indira nagar", "100 feet road")),
                Place("HSR Layout", 12.9116, 77.6474, "IN-KA-BLR", listOf("hsr")),
                Place("Jayanagar", 12.9308, 77.5838, "IN-KA-BLR"),
                Place("JP Nagar", 12.9063, 77.5857, "IN-KA-BLR", listOf("j p nagar")),
                Place("Basavanagudi", 12.9416, 77.5753, "IN-KA-BLR"),
                Place("Malleshwaram", 13.0031, 77.5643, "IN-KA-BLR", listOf("malleswaram")),
                Place("Whitefield", 12.9698, 77.7500, "IN-KA-BLR"),
                Place("MG Road", 12.9756, 77.6050, "IN-KA-BLR", listOf("m g road", "mahatma gandhi road")),
                Place("Church Street", 12.9752, 77.6030, "IN-KA-BLR"),
                Place("Cubbon Park", 12.9763, 77.5929, "IN-KA-BLR"),
                Place("Lalbagh", 12.9507, 77.5848, "IN-KA-BLR", listOf("lal bagh", "lalbagh botanical garden")),
                Place("Ravindra Kalakshetra", 12.9634, 77.5855, "IN-KA-BLR", listOf("kalakshetra", "ravindra kalakshethra")),
                Place("Ranga Shankara", 12.9105, 77.5857, "IN-KA-BLR", listOf("rangashankara")),
                Place("Hebbal", 13.0358, 77.5970, "IN-KA-BLR"),
                Place("Electronic City", 12.8452, 77.6602, "IN-KA-BLR", listOf("e city", "ecity")),
                // North-west Bengaluru
                Place("Dasarahalli", 13.0466, 77.5126, "IN-KA-BLR", listOf("dasarahalli main road", "t dasarahalli")),
                Place("Jalahalli", 13.0468, 77.5470, "IN-KA-BLR"),
                Place("Peenya", 13.0285, 77.5197, "IN-KA-BLR"),
                Place("Yeshwanthpur", 13.0280, 77.5409, "IN-KA-BLR", listOf("yeshwantpur", "yesvantpur")),
                Place("Rajajinagar", 12.9910, 77.5525, "IN-KA-BLR"),
                Place("Vijayanagar", 12.9719, 77.5375, "IN-KA-BLR"),
                Place("Banashankari", 12.9255, 77.5468, "IN-KA-BLR"),
                Place("Yelahanka", 13.1007, 77.5963, "IN-KA-BLR"),
                Place("Marathahalli", 12.9569, 77.7011, "IN-KA-BLR"),
            )
    }
}

data class GeoCoordinate(val lat: Double, val lng: Double)

fun interface LocationProvider {
    /** Last known fix; never blocks. Defaults to the region centre when permission is denied. */
    fun lastKnown(): GeoCoordinate
}
