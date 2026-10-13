package dev.pulse.core.data.booking

import dev.pulse.core.data.ListingItem
import dev.pulse.core.data.RegionPacks

/** A ticketing platform the app can hand a booking to. */
data class BookingPlatform(
    val id: String,
    val name: String,
    /** Two-letter monogram drawn as the platform badge (no third-party logos are bundled). */
    val monogram: String,
    /** Brand-ish badge colour, ARGB. */
    val color: Long,
    /** Android package, used only to say "opens in the app" when it is installed. */
    val androidPackage: String?,
)

/** One button on the booking-options page. */
data class BookingOption(
    val platform: BookingPlatform,
    /** Always an https link. Platforms with verified app links open in their app when installed. */
    val url: String,
    /** What the user should look for once there ("Search for “PVR Orion”"). */
    val hint: String,
)

/**
 * Where a listing can be booked. Eligibility comes from a real destination, not the category alone:
 *  - a merchant's own booking link, or a venue website, opens directly;
 *  - cinemas, theatres, concerts, comedy and event venues are listed by the ticketing platforms, which
 *    are offered at their city page for that kind of show.
 *
 * Only official, fixed platform pages are used (no guessed per-show deep links), so a link is never
 * broken or unrelated. Paytm Insider is part of District now (insider.in redirects there).
 */
object BookingDestinations {
    val BookMyShow = BookingPlatform("bookmyshow", "BookMyShow", "my", 0xFFE5203AL, "com.bt.bms")
    val District = BookingPlatform("district", "District by Zomato", "D", 0xFF6C2BD9L, null)
    val Organiser = BookingPlatform("organiser", "Organiser's booking page", "↗", 0xFF2E7D6BL, null)
    val Venue = BookingPlatform("venue", "Venue website", "↗", 0xFF3B6FD8L, null)

    val platforms = listOf(BookMyShow, District)

    private enum class Kind(val bms: String, val district: String) {
        MOVIES("movies", "movies"),
        PLAYS("plays", "events"),
        EVENTS("events", "events"),
    }

    private fun kindOf(category: String): Kind? = when {
        category == "arts.cinema" -> Kind.MOVIES
        category == "arts.theatre" -> Kind.PLAYS
        category == "arts" || category.startsWith("music") || category.startsWith("comedy") ||
            category.startsWith("events") || category == "sports.stadium" -> Kind.EVENTS
        else -> null
    }

    fun isBookable(item: ListingItem): Boolean = optionsFor(item).isNotEmpty()

    fun optionsFor(item: ListingItem): List<BookingOption> = buildList {
        val name = item.titles["en"] ?: item.titles.values.firstOrNull().orEmpty()
        item.bookingUrl?.takeIf(::isSafeLink)?.let { add(BookingOption(Organiser, it, "Book “$name” on the organiser's page")) }
        val kind = kindOf(item.category)
        if (kind != null) {
            val city = citySlug(item)
            val bms = if (city != null) "https://in.bookmyshow.com/explore/${kind.bms}-$city" else "https://in.bookmyshow.com/"
            add(BookingOption(BookMyShow, bms, "Search for “$name”"))
            add(BookingOption(District, "https://www.district.in/${kind.district}/", "Search for “$name”"))
            item.website?.takeIf(::isSafeLink)?.let { add(BookingOption(Venue, it, "Check shows and tickets on the venue's site")) }
        }
    }

    /** https with a host, nothing else (no javascript:, intent:, or plain http). */
    fun isSafeLink(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("https://", ignoreCase = true)) return false
        val host = u.removePrefix("https://").removePrefix("HTTPS://").substringBefore('/').substringBefore('?')
        return host.contains('.') && host.none { it.isWhitespace() } && !host.startsWith('.') && !host.endsWith('.')
    }

    /** City pages exist only where we know the platform's city slug: today, the bundled Bengaluru region. */
    private fun citySlug(item: ListingItem): String? {
        val blr = RegionPacks.AVAILABLE.first { it.code == "IN-KA-BLR" }
        return if (blr.contains(dev.pulse.core.data.GeoCoordinate(item.lat, item.lng))) "bengaluru" else null
    }
}
