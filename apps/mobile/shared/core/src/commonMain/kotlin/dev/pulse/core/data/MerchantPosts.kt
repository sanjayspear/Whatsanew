package dev.pulse.core.data

import dev.pulse.core.data.booking.BookingDestinations
import dev.pulse.core.prefs.KeyValueStore
import dev.pulse.dsl.PulseJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** What a merchant can announce. Each maps to a search category so posts show up in normal searches. */
enum class OfferingType(val label: String, val category: String) {
    EVENT("Event", "events"),
    SHOW("Play / drama", "arts.theatre"),
    MOVIE("Movie release", "arts.cinema"),
    CONCERT("Concert", "music.live"),
    SHOP("New shop", "shopping"),
    RESTAURANT("Restaurant / café", "food"),
    SERVICE("New service", "services"),
    PROMOTION("Promotion", "shopping"),
}

/** The merchant's form, before validation. */
data class MerchantDraft(
    val title: String = "",
    val type: OfferingType? = null,
    val description: String = "",
    val area: String = "",
    val launchDate: String = "",
    val eventDate: String = "",
    val link: String = "",
)

@Serializable
data class MerchantPost(
    val id: String,
    val title: String,
    val type: OfferingType,
    val description: String,
    val areaName: String,
    val lat: Double,
    val lng: Double,
    val launchAtEpochMs: Long,
    val eventAtEpochMs: Long? = null,
    val link: String? = null,
    val hasImage: Boolean = false,
    val createdAtEpochMs: Long,
) {
    fun toListing(): ListingItem = ListingItem(
        id = id,
        titles = mapOf("en" to title),
        category = type.category,
        lat = lat,
        lng = lng,
        regionCode = REGION,
        timeZone = "Asia/Kolkata",
        address = areaName,
        startsAtEpochMs = eventAtEpochMs,
        endsAtEpochMs = eventAtEpochMs?.plus(4 * 60 * 60_000L),
        website = link,
        bookingUrl = link,
        firstListedAtEpochMs = createdAtEpochMs,
        tagline = mapOf("en" to description.take(120)),
        imageKey = if (hasImage) MerchantPosts.imageKey(id) else null,
        launchAtEpochMs = launchAtEpochMs,
    )

    companion object {
        const val REGION = "MERCHANT"
    }
}

/**
 * Merchant offerings. Posts are validated and moderated on-device (required fields, safe links,
 * dates, duplicates, blocked words), then injected into the local listing cache so they appear in
 * search and in "Search Recently Launched" on this device.
 *
 * Phase B: the same post goes to merchant-service for verification and moderation, and becomes
 * visible to everyone; nothing here needs to change except where [publish] sends it.
 */
class MerchantPosts(
    private val kv: KeyValueStore,
    private val gazetteer: Gazetteer,
    private val nowMs: () -> Long,
    /** Writes listings into the search cache (and removes them). */
    private val upsert: (List<ListingItem>) -> Unit,
    private val delete: (String) -> Unit,
    private val here: () -> GeoCoordinate,
) {
    private val _posts = MutableStateFlow(read())
    val posts: StateFlow<List<MerchantPost>> = _posts.asStateFlow()

    /** Re-injects posts after cache eviction (call once at start-up). */
    fun restore() = upsert(_posts.value.map { it.toListing() })

    sealed interface Outcome {
        data class Published(val post: MerchantPost) : Outcome
        data class Rejected(val problems: List<String>) : Outcome
    }

    fun publish(draft: MerchantDraft, image: ByteArray? = null): Outcome {
        val problems = mutableListOf<String>()
        val title = draft.title.trim().replace(Regex("\\s+"), " ")
        if (title.length < 3) problems += "Add a title (at least 3 characters)"
        if (title.length > 80) problems += "Keep the title under 80 characters"
        if (title.isNotEmpty() && title.count { it.isUpperCase() } > 12 && title == title.uppercase()) problems += "Please don't write the title in capitals"
        val type = draft.type ?: run { problems += "Choose what you're announcing"; null }
        val description = draft.description.trim()
        if (description.length < 10) problems += "Add a short description (at least 10 characters)"
        if (description.length > 500) problems += "Keep the description under 500 characters"
        if (BLOCKED.any { w -> "$title $description".lowercase().contains(w) }) problems += "Remove misleading or inappropriate wording"

        val place = when {
            draft.area.isBlank() -> null
            draft.area.trim().equals(CURRENT_LOCATION, ignoreCase = true) -> Place(CURRENT_LOCATION, here().lat, here().lng, "HERE")
            else -> gazetteer.lookup(draft.area.trim()) ?: gazetteer.findIn(draft.area.trim().lowercase())
        }
        if (draft.area.isBlank()) problems += "Add the area (e.g. Indiranagar) or use your current location"
        else if (place == null) problems += "We couldn't find “${draft.area.trim()}”. Try a nearby neighbourhood name"

        val today = startOfDay(nowMs())
        val launch = if (draft.launchDate.isBlank()) today else parseDate(draft.launchDate)
        if (launch == null) problems += "Launch date must look like 2026-11-05"
        else if (launch < today - DAY) problems += "Launch date can't be in the past"
        else if (launch > today + 365 * DAY) problems += "Launch date must be within a year"
        val event = if (draft.eventDate.isBlank()) null else parseDate(draft.eventDate)
        if (draft.eventDate.isNotBlank() && event == null) problems += "Event date must look like 2026-11-05"
        if (event != null && launch != null && event < launch) problems += "The event can't be before the launch date"
        if (type in setOf(OfferingType.EVENT, OfferingType.SHOW, OfferingType.CONCERT) && draft.eventDate.isBlank()) problems += "Add the event date"

        val link = draft.link.trim().ifEmpty { null }
        if (link != null && !BookingDestinations.isSafeLink(link)) problems += "Links must start with https:// (we only open secure links)"

        if (_posts.value.any { it.title.equals(title, ignoreCase = true) && it.areaName.equals(place?.name, ignoreCase = true) }) {
            problems += "You've already posted “$title” in ${place?.name}"
        }
        if (problems.isNotEmpty() || type == null || place == null || launch == null) return Outcome.Rejected(problems)

        val post = MerchantPost(
            id = "merchant:${nowMs().toString(36)}${(0..999).random()}",
            title = title,
            type = type,
            description = description,
            areaName = place.name,
            lat = place.lat,
            lng = place.lng,
            launchAtEpochMs = launch,
            // Events show at 7 pm local on their date unless a time is added later.
            eventAtEpochMs = event?.plus(19 * 60 * 60_000L),
            link = link,
            hasImage = image != null,
            createdAtEpochMs = nowMs(),
        )
        image?.let { kv.put(imageKey(post.id), kotlin.io.encoding.Base64.encode(it)) }
        write(listOf(post) + _posts.value)
        upsert(listOf(post.toListing()))
        return Outcome.Published(post)
    }

    fun remove(id: String) {
        write(_posts.value.filterNot { it.id == id })
        kv.remove(imageKey(id))
        delete(id)
    }

    fun clearAll() = _posts.value.forEach { remove(it.id) }

    fun image(id: String): ByteArray? = kv.get(imageKey(id))?.let { runCatching { kotlin.io.encoding.Base64.decode(it) }.getOrNull() }

    private fun write(list: List<MerchantPost>) {
        kv.put(KEY, PulseJson.encodeToString(ListSerializer(MerchantPost.serializer()), list))
        _posts.value = list
    }

    private fun read(): List<MerchantPost> = kv.get(KEY)
        ?.let { runCatching { PulseJson.decodeFromString(ListSerializer(MerchantPost.serializer()), it) }.getOrNull() }
        .orEmpty()

    companion object {
        const val CURRENT_LOCATION = "Current location"
        private const val KEY = "merchant.posts"
        private const val DAY = 24 * 60 * 60_000L
        private const val IST_OFFSET = (5 * 60 + 30) * 60_000L
        fun imageKey(id: String) = "merchant.image.$id"

        /** Simple on-device moderation; server moderation replaces this in phase B. */
        private val BLOCKED = listOf("guaranteed returns", "100% free", "click here", "lottery", "get rich", "xxx", "adult")

        /** Midnight IST of the given instant, as epoch ms. */
        private fun startOfDay(ms: Long) = (ms + IST_OFFSET) / DAY * DAY - IST_OFFSET

        /** "2026-11-05" -> midnight IST that day. */
        fun parseDate(s: String): Long? = runCatching { kotlinx.datetime.LocalDate.parse(s.trim()) }.getOrNull()
            ?.let { it.toEpochDays().toLong() * DAY - IST_OFFSET }
    }
}
