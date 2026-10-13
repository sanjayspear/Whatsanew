package dev.pulse.core.data.osm

/**
 * Pulse taxonomy key <-> OpenStreetMap tags. Data, not logic: adding a category is one line.
 * Matching is prefix-aware, so `food` covers every `food.*` entry.
 */
object OsmCategories {

    /** One OSM tag filter, e.g. key=amenity, values=[cafe]. */
    data class TagFilter(val key: String, val values: List<String>, val extra: Pair<String, String>? = null)

    /** Declared before [mapping], which uses it (object properties initialise in order). */
    val OFFICE_VALUES = listOf(
        "company", "it", "government", "financial", "insurance", "ngo", "research", "telecommunication", "coworking",
        "educational_institution", "consulting", "association", "accountant", "advertising_agency", "architect",
        "employment_agency", "logistics", "travel_agent", "estate_agent", "lawyer", "engineer", "diplomatic",
        "political_party", "newspaper", "property_management", "tax_advisor", "religion", "union", "yes",
    )

    val mapping: List<Pair<String, TagFilter>> = listOf(
        // Juice shops are mostly cafés / fast food with "juice" in the name or cuisine; before food.cafe so they win.
        "food.juice" to TagFilter("amenity", listOf("cafe", "fast_food", "ice_cream"), "cuisine" to "juice|smoothie|milkshake"),
        "food.juice" to TagFilter("amenity", listOf("cafe", "fast_food", "ice_cream"), "name" to JUICE_NAME),
        "food.juice" to TagFilter("shop", listOf("beverages", "juice"), "name" to JUICE_NAME),
        "food.juice" to TagFilter("shop", listOf("juice")),
        "food.ice_cream" to TagFilter("amenity", listOf("ice_cream")),
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
        // Religion decides the kind of place, never the name: "Temple Tree Cafe" is a café.
        "worship.temple" to TagFilter("amenity", listOf("place_of_worship"), "religion" to "hindu"),
        "worship.church" to TagFilter("amenity", listOf("place_of_worship"), "religion" to "christian"),
        "worship.mosque" to TagFilter("amenity", listOf("place_of_worship"), "religion" to "muslim"),
        "worship.gurudwara" to TagFilter("amenity", listOf("place_of_worship"), "religion" to "sikh"),
        "worship.jain" to TagFilter("amenity", listOf("place_of_worship"), "religion" to "jain"),
        "worship.buddhist" to TagFilter("amenity", listOf("place_of_worship"), "religion" to "buddhist"),
        "worship" to TagFilter("amenity", listOf("place_of_worship")),
        "beauty.salon" to TagFilter("shop", listOf("hairdresser", "beauty")),
        "beauty.spa" to TagFilter("shop", listOf("massage")),
        "beauty.cosmetics" to TagFilter("shop", listOf("cosmetics")),
        "beauty" to TagFilter("shop", listOf("hairdresser", "beauty", "massage", "cosmetics")),
        // No OSM tag for "IT park": commercial land / office campuses with a tech-park name (see tools/osm-pack).
        "business.it_park" to TagFilter("landuse", listOf("commercial", "industrial"), "name" to TECH_PARK_NAME),
        "business.it_park" to TagFilter("office", listOf("it", "company"), "name" to TECH_PARK_NAME),
        // Every other named office (companies, IT firms, banks' offices, government...): searched by name.
        "business.office" to TagFilter("office", OFFICE_VALUES),
        "shopping.grocery" to TagFilter("shop", listOf("supermarket", "convenience", "greengrocer")),
        "shopping.furniture" to TagFilter("shop", listOf("furniture")),
        "shopping.hardware" to TagFilter("shop", listOf("hardware")),
        "shopping.pet" to TagFilter("shop", listOf("pet")),
        "health.optician" to TagFilter("shop", listOf("optician")),
        "education.school" to TagFilter("amenity", listOf("school")),
        "education.college" to TagFilter("amenity", listOf("college", "university")),
        "education.library" to TagFilter("amenity", listOf("library")),
        "sports.stadium" to TagFilter("leisure", listOf("stadium")),
        "services.police" to TagFilter("amenity", listOf("police")),
        "services.post" to TagFilter("amenity", listOf("post_office")),
        "services.bus" to TagFilter("amenity", listOf("bus_station")),
        "services.laundry" to TagFilter("shop", listOf("laundry", "dry_cleaning")),
        "services.repair" to TagFilter("shop", listOf("car_repair", "motorcycle_repair")),
        "health.pharmacy" to TagFilter("amenity", listOf("pharmacy")),
        "health.hospital" to TagFilter("amenity", listOf("hospital", "clinic")),
        "services.atm" to TagFilter("amenity", listOf("atm", "bank")),
        "services.fuel" to TagFilter("amenity", listOf("fuel", "charging_station")),
    )

    /** Kept in step with TECH_PARK_NAME in tools/osm-pack/build_region_pack.py. */
    const val TECH_PARK_NAME = "[Tt]ech ?[Pp]ark|IT [Pp]ark|[Ss]oftware [Pp]ark|[Tt]echnology [Pp]ark|[Bb]usiness [Pp]ark|[Tt]ech [Cc]ampus|SEZ|[Tt]echzone|[Tt]ech [Hh]ub"
    const val JUICE_NAME = "[Jj]uice|JUICE|[Ss]moothie|[Mm]ilkshake|[Ss]ugarcane"

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
