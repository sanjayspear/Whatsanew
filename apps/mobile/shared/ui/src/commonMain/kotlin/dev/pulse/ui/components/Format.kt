package dev.pulse.ui.components

import dev.pulse.core.data.ListingItem
import dev.pulse.dsl.Freshness
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.TimePreset
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Display strings. Phase 1 ships English plus a Kannada sample; Phase 2 moves these to Compose
 * Multiplatform resources per locale. Nothing here is user content: listing text always comes
 * localized from the catalog.
 */
internal object Format {

    private val categoryNames = mapOf(
        "food.cafe" to "Cafés", "food.cafe.specialty" to "Specialty coffee", "food.restaurant" to "Restaurants",
        "food.restaurant.south_indian" to "South Indian", "food.bar" to "Bars & breweries", "arts.theatre" to "Theatre",
        "arts.cinema" to "Cinema", "arts.gallery" to "Galleries", "music.live" to "Live music", "comedy.standup" to "Stand-up",
        "fitness" to "Fitness", "fitness.yoga" to "Yoga", "fitness.gym" to "Gyms", "shopping" to "Shopping",
        "shopping.books" to "Bookstores", "shopping.market" to "Markets", "gems" to "Hidden gems",
        "food" to "Food", "fashion" to "Fashion", "fashion.ethnic" to "Ethnic wear", "fashion.footwear" to "Footwear",
        "fashion.streetwear" to "Streetwear", "fashion.thrift" to "Thrift", "fashion.boutique" to "Boutiques",
        "stay" to "Stays", "stay.hotel" to "Hotels & stays", "events" to "Events", "events.workshop" to "Workshops",
        "arts" to "Arts & culture", "arts.museum" to "Museums", "music" to "Live music", "comedy" to "Comedy",
        "gems.park" to "Parks", "gems.lake" to "Lakes", "shopping.mall" to "Malls", "shopping.electronics" to "Electronics",
        "health.pharmacy" to "Pharmacies", "health.hospital" to "Hospitals & clinics", "services.atm" to "ATMs & banks",
        "services.fuel" to "Fuel & charging", "food.bakery" to "Bakeries", "fitness.gym" to "Gyms",
        "worship" to "Places of worship", "worship.temple" to "Temples", "worship.church" to "Churches",
        "worship.mosque" to "Mosques", "worship.gurudwara" to "Gurudwaras", "worship.jain" to "Jain temples",
        "worship.buddhist" to "Buddhist temples", "beauty" to "Beauty", "beauty.salon" to "Salons",
        "beauty.spa" to "Spas & massage", "beauty.cosmetics" to "Cosmetics", "food.juice" to "Juice shops",
        "food.ice_cream" to "Ice cream", "business.it_park" to "IT parks", "shopping.grocery" to "Groceries",
        "shopping.furniture" to "Furniture", "shopping.hardware" to "Hardware", "shopping.pet" to "Pet shops",
        "health.optician" to "Opticians", "education.school" to "Schools", "education.college" to "Colleges",
        "education.library" to "Libraries", "sports.stadium" to "Stadiums", "services.police" to "Police",
        "services.post" to "Post offices", "services.bus" to "Bus stations", "services.laundry" to "Laundry",
        "services.repair" to "Vehicle repair", "services" to "Services",
        "business.office" to "Offices & companies", "places" to "Places",
    )
    private val kannada = mapOf("food.cafe" to "ಕೆಫೆಗಳು", "arts.theatre" to "ರಂಗಭೂಮಿ")

    fun category(key: String, lang: String = "en"): String =
        (if (lang.startsWith("kn")) kannada[key] else null)
            ?: categoryNames[key]
            ?: categoryNames.entries.firstOrNull { key.startsWith(it.key + ".") }?.value
            ?: key.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun headline(q: ListingQuery, lang: String): String {
        val what = when {
            q.categories.toSet() == dev.pulse.core.ai.Lexicon.EVENTS.toSet() -> "Events"
            q.categories.isNotEmpty() -> q.categories.take(2).joinToString(" & ") { category(it, lang) } +
                if (q.categories.size > 2) " & more" else ""
            q.text != null -> "“${q.text}”"
            else -> "Everything around you"
        }
        return when (q.freshness) {
            Freshness.NEW -> if (q.categories.isEmpty() && q.text == null) "Recently launched" else "New ${what.lowercase()}"
            Freshness.TRENDING -> "Trending ${what.lowercase()}"
            null -> what
        }
    }

    fun chips(q: ListingQuery): List<String> = buildList {
        q.near?.let { near ->
            add(
                when (val a = near.anchor) {
                    is GeoAnchor.Place -> a.name
                    GeoAnchor.Here -> "Near you"
                    is GeoAnchor.Point -> "Pinned spot"
                },
            )
            add(distance(near.radiusMeters.toDouble(), within = true))
        }
        q.time?.preset?.let { add(timePreset(it)) }
        if (q.openNow == true) add("Open now")
        q.priceLevelMax?.let { add("Up to " + "₹".repeat(it)) }
    }

    fun timePreset(p: TimePreset) = when (p) {
        TimePreset.NOW -> "Right now"
        TimePreset.TODAY -> "Today"
        TimePreset.TONIGHT -> "Tonight"
        TimePreset.TOMORROW -> "Tomorrow"
        TimePreset.THIS_WEEKEND -> "This weekend"
        TimePreset.NEXT_7_DAYS -> "Next 7 days"
    }

    fun distance(meters: Double, within: Boolean = false): String {
        val prefix = if (within) "≤ " else ""
        return if (meters < 1_000) "$prefix${(meters / 10).roundToInt() * 10} m"
        else {
            val km = (meters / 100).roundToInt() / 10.0
            prefix + (if (km % 1.0 == 0.0) "${km.toInt()}" else "$km") + " km"
        }
    }

    fun price(item: ListingItem): String? {
        val minor = item.priceMinor
        val level = item.priceLevel
        return when {
            minor != null && item.currency == "INR" -> if (minor == 0L) "Free" else "₹${minor / 100}"
            level != null -> "₹".repeat(level)
            else -> null
        }
    }

    fun eventTime(item: ListingItem): String? {
        val start = item.startsAtEpochMs ?: return null
        val local = Instant.fromEpochMilliseconds(start).toLocalDateTime(TimeZone.of(item.timeZone))
        val day = when (local.dayOfWeek) {
            DayOfWeek.MONDAY -> "Mon"; DayOfWeek.TUESDAY -> "Tue"; DayOfWeek.WEDNESDAY -> "Wed"; DayOfWeek.THURSDAY -> "Thu"
            DayOfWeek.FRIDAY -> "Fri"; DayOfWeek.SATURDAY -> "Sat"; else -> "Sun"
        }
        val h = local.hour % 12
        val hour = if (h == 0) 12 else h
        val ampm = if (local.hour < 12) "AM" else "PM"
        val min = local.minute.toString().padStart(2, '0')
        return "$day · $hour:$min $ampm"
    }

    fun isNew(item: ListingItem, nowMs: Long) = nowMs - item.firstListedAtEpochMs < 90L * 24 * 60 * 60_000

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "14 Oct" (adds the year when it isn't this year). */
    fun date(epochMs: Long, nowMs: Long, zone: String = "Asia/Kolkata"): String {
        val tz = TimeZone.of(zone)
        val d = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        val thisYear = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).year
        return "${d.day} ${months[d.month.ordinal]}" + if (d.year != thisYear) " ${d.year}" else ""
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago", then a date. */
    fun ago(epochMs: Long, nowMs: Long): String {
        val min = (nowMs - epochMs) / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 24 * 60 -> "${min / 60} h ago"
            min < 7 * 24 * 60 -> "${min / (24 * 60)} days ago".replace("1 days", "1 day")
            else -> date(epochMs, nowMs)
        }
    }

    /**
     * What marks a listing as recently launched: merchant launch / event date, or when it first
     * appeared on the map. Null for long-established places.
     */
    fun launchLine(item: ListingItem, nowMs: Long): String? {
        item.launchAtEpochMs?.let { at ->
            return if (at > nowMs) "Launches ${date(at, nowMs)}" else "Launched ${date(at, nowMs)}"
        }
        if (!isNew(item, nowMs) || item.firstListedAtEpochMs <= 0) return null
        return "New · added ${date(item.firstListedAtEpochMs, nowMs)}"
    }
}
