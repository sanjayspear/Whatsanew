package dev.pulse.core.data.osm

/**
 * Pulse taxonomy key <-> OpenStreetMap tags. Data, not logic: adding a category is one line.
 * Matching is prefix-aware, so `food` covers every `food.*` entry.
 */
object OsmCategories {

    /** One OSM tag filter, e.g. key=amenity, values=[cafe]. */
    data class TagFilter(val key: String, val values: List<String>, val extra: Pair<String, String>? = null)

    val mapping: List<Pair<String, TagFilter>> = listOf(
        "food.cafe" to TagFilter("amenity", listOf("cafe")),
        "food.restaurant.south_indian" to TagFilter("amenity", listOf("restaurant", "fast_food"), "cuisine" to "south_indian|indian|vegetarian|regional"),
        "food.restaurant" to TagFilter("amenity", listOf("restaurant", "fast_food", "food_court")),
        "food.bar" to TagFilter("amenity", listOf("bar", "pub", "biergarten")),
        "food.bakery" to TagFilter("shop", listOf("bakery", "confectionery", "pastry")),
        "food" to TagFilter("amenity", listOf("restaurant", "cafe", "fast_food", "food_court", "ice_cream")),
        "stay.hotel" to TagFilter("tourism", listOf("hotel", "guest_house", "hostel", "motel", "apartment")),
        "stay" to TagFilter("tourism", listOf("hotel", "guest_house", "hostel", "motel", "apartment", "camp_site")),
        "fashion.footwear" to TagFilter("shop", listOf("shoes")),
        "fashion.ethnic" to TagFilter("shop", listOf("fabric", "clothes", "tailor"), "clothes" to "traditional|saree|women"),
        "fashion" to TagFilter("shop", listOf("clothes", "boutique", "fashion", "fashion_accessories", "shoes", "tailor", "jewelry", "bag")),
        "shopping.mall" to TagFilter("shop", listOf("mall", "department_store")),
        "shopping.books" to TagFilter("shop", listOf("books", "stationery")),
        "shopping.electronics" to TagFilter("shop", listOf("electronics", "mobile_phone", "computer")),
        "shopping.market" to TagFilter("amenity", listOf("marketplace")),
        "shopping" to TagFilter("shop", listOf("mall", "department_store", "supermarket", "electronics", "books", "gift", "variety_store", "sports", "toys")),
        "fitness.yoga" to TagFilter("sport", listOf("yoga")),
        "fitness.swimming" to TagFilter("leisure", listOf("swimming_pool", "sports_centre"), "sport" to "swimming"),
        "fitness.gym" to TagFilter("leisure", listOf("fitness_centre")),
        "fitness" to TagFilter("leisure", listOf("fitness_centre", "sports_centre", "fitness_station")),
        "gems.park" to TagFilter("leisure", listOf("park", "garden")),
        "gems.lake" to TagFilter("water", listOf("lake", "reservoir", "pond")),
        "gems" to TagFilter("tourism", listOf("attraction", "viewpoint", "artwork")),
        "arts.theatre" to TagFilter("amenity", listOf("theatre")),
        "arts.cinema" to TagFilter("amenity", listOf("cinema")),
        "arts.museum" to TagFilter("tourism", listOf("museum", "gallery")),
        "arts" to TagFilter("amenity", listOf("theatre", "cinema", "arts_centre")),
        "music" to TagFilter("amenity", listOf("music_venue", "nightclub")),
        "comedy" to TagFilter("amenity", listOf("theatre", "events_venue")),
        "events" to TagFilter("amenity", listOf("events_venue", "community_centre", "conference_centre", "exhibition_centre")),
        "health.pharmacy" to TagFilter("amenity", listOf("pharmacy")),
        "health.hospital" to TagFilter("amenity", listOf("hospital", "clinic")),
        "services.atm" to TagFilter("amenity", listOf("atm", "bank")),
        "services.fuel" to TagFilter("amenity", listOf("fuel", "charging_station")),
    )

    /** Filters for a set of requested categories (each request expands to itself and its children). */
    fun filtersFor(categories: List<String>): List<Pair<String, TagFilter>> =
        if (categories.isEmpty()) mapping.filter { (k, _) -> '.' !in k } // vague request: one of each root
        else mapping.filter { (k, _) -> categories.any { c -> k == c || k.startsWith("$c.") || c.startsWith("$k.") } }

    /** Most specific Pulse category for an OSM element's tags. */
    fun categoryOf(tags: Map<String, String>): String? =
        mapping.firstOrNull { (_, f) ->
            tags[f.key] in f.values && (f.extra == null || tags[f.extra.first]?.let { v -> Regex(f.extra.second).containsMatchIn(v) } == true)
        }?.first ?: mapping.firstOrNull { (_, f) -> tags[f.key] in f.values && f.extra == null }?.first
}
