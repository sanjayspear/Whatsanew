package dev.pulse.core.data

import dev.pulse.dsl.TimePreset
import dev.pulse.dsl.TimeWindow

/**
 * Offline seed for Phase 1 demos and first launch with no network. **All venue names, phone
 * numbers and shows are fictional**; only the localities and public landmarks are real. Event
 * times are generated relative to "now" so demos always have something on this weekend.
 */
object SampleCatalog {

    private const val DAY = 24 * 60 * 60_000L
    private const val HOUR = 60 * 60_000L
    private const val BLR = "IN-KA-BLR"
    private const val IST = "Asia/Kolkata"

    fun listings(nowMs: Long): List<ListingItem> {
        val weekend = TimeWindows.resolve(TimeWindow(TimePreset.THIS_WEEKEND), nowMs, IST)!!
        // Start of Saturday, or of today if the weekend has already begun.
        val sat = if (weekend.fromMs > nowMs) weekend.fromMs else TimeWindows.startOfDay(nowMs, IST)
        fun at(dayOffset: Int, hour: Double) = sat + dayOffset * DAY + (hour * HOUR).toLong()
        fun listed(daysAgo: Int) = nowMs - daysAgo * DAY

        var n = 0
        fun item(
            title: String, category: String, lat: Double, lng: Double, address: String, listedDaysAgo: Int,
            price: Int? = null, rating: Float? = null, tagline: String? = null, kn: String? = null,
            startsAt: Long? = null, durationH: Double = 2.5, priceMinor: Long? = null, video: String? = null,
            opens: Int? = null, closes: Int? = null,
        ) = ListingItem(
            id = "seed-${++n}",
            titles = buildMap { put("en", title); kn?.let { put("kn", it) } },
            category = category, lat = lat, lng = lng, regionCode = BLR, timeZone = IST, address = address,
            priceLevel = price, priceMinor = priceMinor, currency = priceMinor?.let { "INR" },
            startsAtEpochMs = startsAt, endsAtEpochMs = startsAt?.let { it + (durationH * HOUR).toLong() },
            phone = "+9180000${(10000 + n * 137).toString().takeLast(5)}", videoId = video,
            firstListedAtEpochMs = listed(listedDaysAgo), opensAtMinute = opens, closesAtMinute = closes,
            rating = rating, tagline = tagline?.let { mapOf("en" to it) }.orEmpty(),
        )

        return listOf(
            // --- Cafes around Koramangala ---
            item("Ember & Bean", "food.cafe", 12.9345, 77.6190, "5th Block, Koramangala", 12, 2, 4.6f,
                "Single-origin pour-overs and sourdough from 8am", kn = "ಎಂಬರ್ ಅಂಡ್ ಬೀನ್", opens = 480, closes = 1380),
            item("Filter Theory", "food.cafe", 12.9368, 77.6271, "80 Feet Road, Koramangala", 25, 1, 4.5f,
                "Brass-tumbler filter coffee, reimagined", opens = 420, closes = 1320),
            item("Slow Lane Roasters", "food.cafe.specialty", 12.9300, 77.6230, "1st Block, Koramangala", 40, 3, 4.7f,
                "Micro-lot roasts and a quiet reading loft", opens = 540, closes = 1260),
            item("Courtyard Kaapi", "food.cafe", 12.9395, 77.6205, "6th Block, Koramangala", 520, 2, 4.3f,
                "A Koramangala classic under the rain tree", opens = 450, closes = 1350),
            item("Monsoon Room", "food.cafe", 12.9730, 77.6400, "12th Main, Indiranagar", 8, 2, 4.4f,
                "Rain-glass windows, cardamom cold brew"),
            item("HSR Brew Lab", "food.cafe", 12.9120, 77.6460, "Sector 2, HSR Layout", 18, 2, 4.2f,
                "Nitro coffee and board-game Thursdays"),

            // --- Theatre near Ravindra Kalakshetra, this weekend ---
            item("Yayati — Kannada Repertory", "arts.theatre", 12.9634, 77.5855, "Ravindra Kalakshetra", 9, 2, 4.8f,
                "Girish Karnad's classic, staged in the round", kn = "ಯಯಾತಿ", startsAt = at(0, 19.0),
                priceMinor = 30_000, video = "demo-yayati"),
            item("Tughlaq (Revival)", "arts.theatre", 12.9634, 77.5855, "Ravindra Kalakshetra", 15, 2, 4.6f,
                "A sultan's dream, a modern mirror", startsAt = at(1, 16.0), priceMinor = 40_000, video = "demo-tughlaq"),
            item("Midsummer at Cubbon Park", "arts.theatre", 12.9763, 77.5929, "Cubbon Park Bandstand", 6, 1, 4.5f,
                "Open-air Shakespeare at golden hour", startsAt = at(0, 17.5), priceMinor = 0, video = "demo-midsummer"),
            item("The Lighthouse Keeper", "arts.theatre", 12.9640, 77.5868, "Town Hall, JC Road", 4, 2, 4.3f,
                "English improv: no two nights alike", startsAt = at(0, 20.5), priceMinor = 50_000),
            item("Malleshwaram Courtyard Plays", "arts.theatre", 13.0031, 77.5643, "18th Cross, Malleshwaram", 30, 1, 4.4f,
                "Short Kannada plays in a 1930s courtyard", startsAt = at(1, 11.0), priceMinor = 20_000, video = "demo-courtyard"),
            item("Hamlet in Konkani", "arts.theatre", 12.9634, 77.5855, "Ravindra Kalakshetra", 3, 2, 4.2f,
                "Next month's premiere", startsAt = at(30, 19.0), priceMinor = 35_000),

            // --- Other weekend culture ---
            item("Ray Retrospective: Charulata", "arts.cinema", 12.9416, 77.5753, "Basavanagudi Film Society", 20, 1, 4.7f,
                "Restored 4K print, Bengali with subtitles", startsAt = at(1, 18.0), priceMinor = 15_000, video = "demo-charulata"),
            item("Indie Night on Church Street", "music.live", 12.9752, 77.6030, "Church Street Social Hall", 10, 2, 4.4f,
                "Three local bands, one rooftop", startsAt = at(0, 21.0), priceMinor = 60_000, video = "demo-indie"),
            item("Koramangala Comedy Cellar", "comedy.standup", 12.9352, 77.6150, "4th Block, Koramangala", 14, 2, 4.5f,
                "New material night, Kannada + English", startsAt = at(0, 20.0), priceMinor = 49_900),

            // --- South Indian (for camera + voice "dosa near me") ---
            item("Ammi's Dosa Corner", "food.restaurant.south_indian", 12.9338, 77.6262, "7th Block, Koramangala", 200, 1, 4.6f,
                "Paper-thin ghee roast dosa since 1998", kn = "ಅಮ್ಮಿಯ ದೋಸೆ ಕಾರ್ನರ್", opens = 390, closes = 1350),
            item("Benne Dose House", "food.restaurant.south_indian", 12.9330, 77.6290, "8th Block, Koramangala", 60, 1, 4.7f,
                "Davangere-style butter dosa, crisp edges", opens = 420, closes = 1290),
            item("Udupi Grand", "food.restaurant.south_indian", 12.9400, 77.6230, "Sony World Junction", 900, 2, 4.1f,
                "Thalis, filter coffee, no fuss", opens = 360, closes = 1380),

            // --- Fitness / shopping / hidden gems ---
            item("Rooftop Vinyasa Collective", "fitness.yoga", 12.9370, 77.6220, "Koramangala 3rd Block", 22, 2, 4.8f,
                "Sunrise flow above the treetops", opens = 330, closes = 600),
            item("Blossom Book Stacks", "shopping.books", 12.9757, 77.6035, "Church Street", 2_000, 1, 4.8f,
                "Three floors of second-hand treasure", opens = 630, closes = 1290),
            item("Sunday Soul Santhe", "shopping.market", 12.9610, 77.6440, "Domlur Grounds", 35, 1, 4.3f,
                "Makers' market: indigo, ceramics, cold-pressed", startsAt = at(1, 10.0), durationH = 8.0),
            item("The Stepwell Garden", "gems", 12.9507, 77.5848, "Lalbagh West Gate", 45, 1, 4.9f,
                "A hidden step-garden most locals walk past"),
        )
    }
}
