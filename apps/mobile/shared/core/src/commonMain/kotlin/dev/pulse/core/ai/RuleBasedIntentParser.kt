package dev.pulse.core.ai

import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.SearchDefaults
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.ActionSpec
import dev.pulse.dsl.Freshness
import dev.pulse.dsl.FrameSource
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.IntentFrame
import dev.pulse.dsl.LayoutEdit
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.Mood
import dev.pulse.dsl.Task
import dev.pulse.dsl.ThemePatch
import dev.pulse.dsl.TimePreset
import dev.pulse.dsl.TimeWindow

/**
 * The deterministic bottom rung of the AI ladder: lexicon + gazetteer + regex slot filling.
 * It runs in well under 5 ms on any phone, needs no model, and handles the bulk of everyday
 * phrasings, which is why first paint never waits on an LLM. Its confidence score decides
 * whether the on-device LLM is consulted at all.
 */
class RuleBasedIntentParser(private val gazetteer: Gazetteer) {

    /** True if [placeName] (or one of its aliases) appears in what the user actually said. */
    fun mentionsPlace(input: IntentInput, placeName: String): Boolean {
        val said = gazetteer.findIn(normalize(listOf(input.text, *input.visionTags.toTypedArray()).joinToString(" ")))
        return said != null && said.name.equals(gazetteer.lookup(placeName)?.name ?: placeName, ignoreCase = true)
    }

    fun parse(input: IntentInput, context: IntentContext): IntentFrame {
        val t = normalize(listOf(input.text, *input.visionTags.toTypedArray()).joinToString(" "))
        if (t.isBlank()) return IntentFrame(Task.UNKNOWN, confidence = 0f)

        customizeTheme(t)?.let { return it }
        customizeLayout(t)?.let { return it }
        if (context.hasSurface) act(t)?.let { return it }

        val (query, confidence) = extractQuery(t)
        val refine = context.hasSurface &&
            Lexicon.refineStarts.any { t.startsWith(it) } &&
            query.categories.isEmpty() && query.near == null
        return when {
            refine -> IntentFrame(Task.REFINE, query = query, confidence = 0.85f, source = FrameSource.RULES)
            confidence > 0f -> IntentFrame(Task.DISCOVER, query = query, confidence = confidence, source = FrameSource.RULES)
            else -> IntentFrame(Task.UNKNOWN, confidence = 0.2f, source = FrameSource.RULES)
        }
    }

    private fun customizeTheme(t: String): IntentFrame? {
        val mentionsTheme = Lexicon.themeNouns.any(t::hasPhrase)
        val textSize = Lexicon.biggerText.any(t::hasPhrase) || Lexicon.smallerText.any(t::hasPhrase)
        if (!mentionsTheme && !textSize) return null

        val mood = Lexicon.moods.firstOrNull { t.hasPhrase(it.first) }?.let { Mood.valueOf(it.second) }
        val quieter = Lexicon.quieter.any(t::hasPhrase)
        val louder = Lexicon.louder.any(t::hasPhrase)
        val patch = ThemePatch(
            mood = mood,
            motionScale = when { quieter -> 0.5f; louder -> 1.3f; else -> null },
            saturation = when { quieter -> 0.75f; louder -> 1.15f; else -> null },
            blurScale = if (quieter) 1.2f else null,
            typeScale = when {
                Lexicon.biggerText.any(t::hasPhrase) -> 1.2f
                Lexicon.smallerText.any(t::hasPhrase) -> 0.9f
                else -> null
            },
        )
        if (patch == ThemePatch()) return null
        return IntentFrame(Task.CUSTOMIZE_THEME, theme = patch, confidence = if (mood != null) 0.92f else 0.8f)
    }

    private fun customizeLayout(t: String): IntentFrame? {
        val verb = Lexicon.layoutVerbs.firstOrNull { t.hasPhrase(it) } ?: return null
        val role = Lexicon.roles.firstOrNull { t.hasPhrase(it.first) }?.second ?: return null
        val top = Lexicon.toTop.any(t::hasPhrase)
        val bottom = Lexicon.toBottom.any(t::hasPhrase)
        val edit = when {
            verb in setOf("hide", "remove", "drop") -> LayoutEdit.Hide(role)
            top -> LayoutEdit.Move(role, 0)
            bottom -> LayoutEdit.Move(role, -1)
            // "show the map" is a layout edit; "show me cafes on a map" is a search.
            verb == "show" && Lexicon.categories.none { t.hasPhrase(it.first) } -> LayoutEdit.Show(role)
            else -> return null
        }
        return IntentFrame(Task.CUSTOMIZE_LAYOUT, layout = listOf(edit), confidence = 0.95f)
    }

    private fun act(t: String): IntentFrame? {
        val verb = Lexicon.actVerbs.firstOrNull { t.startsWith(it) } ?: return null
        val index = Lexicon.ordinals.firstOrNull { t.hasPhrase(it.first) }?.second
            ?: if (listOf("it", "this", "that", "them").any(t::hasPhrase)) 0 else return null
        val kind = when (verb) {
            "call" -> ActionKind.CALL
            "directions", "navigate" -> ActionKind.NAVIGATE
            "book" -> ActionKind.BOOK
            "share" -> ActionKind.SHARE
            else -> ActionKind.SAVE
        }
        return IntentFrame(Task.ACT, action = ActionSpec(kind, mapOf("target" to "results[$index]")), confidence = 0.9f)
    }

    private fun extractQuery(t: String): Pair<ListingQuery, Float> {
        // Strip the place name first, so "Cubbon Park" doesn't also read as the "park" category.
        val placeMatch = gazetteer.matchIn(t)
        val forCategories = correctSpelling(placeMatch?.let { (alias, _) -> " $t ".replace(" $alias ", " ").trim() } ?: t)
        val hits = Lexicon.categories.filter { forCategories.hasPhrase(it.first) }
            // "IT park" is not also a "park"; "jain temple" is not also a generic "temple".
            .let { all -> all.filterNot { h -> all.any { o -> o.first != h.first && o.first.hasPhrase(h.first) } } }
        val categories = hits.flatMap { it.second.categories }.distinct()
            // Drop a parent when a child is present ("food.restaurant" + "food.restaurant.south_indian").
            .let { cats -> cats.filterNot { c -> cats.any { it != c && it.startsWith("$c.") } } }
        val dishText = hits.firstNotNullOfOrNull { it.second.text }

        val place = placeMatch?.second
        // Area the offline pack doesn't know ("near Mysuru Palace"): keep the name; the app geocodes it.
        val unknownPlace = if (place == null) UNKNOWN_PLACE.find(forCategories)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.split(' ').none { w -> w in NOT_A_PLACE } && it.length >= 3 }
            // "in nearby cinemas" names what, not where: a place needs a word the lexicons don't explain.
            ?.takeIf { it.split(' ').any { w -> w !in KNOWN_TERMS && w !in STOP_WORDS && w !in Lexicon.correctable } }
            ?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) } else null
        val here = Lexicon.hereWords.any(t::hasPhrase)
        val radius = RADIUS.find(t)?.let { m -> toMeters(m.groupValues[1].toDouble(), m.groupValues[2]) }
        val near = when {
            place != null -> GeoFilter(GeoAnchor.Place(place.name), radius ?: SearchDefaults.radiusMeters)
            unknownPlace != null -> GeoFilter(GeoAnchor.Place(unknownPlace), radius ?: SearchDefaults.radiusMeters)
            here -> GeoFilter(GeoAnchor.Here, radius ?: SearchDefaults.radiusMeters)
            radius != null -> GeoFilter(GeoAnchor.Here, radius)
            else -> null
        }
        val time = Lexicon.timePresets.firstOrNull { t.hasPhrase(it.first) }?.let { TimeWindow(TimePreset.valueOf(it.second)) }
        val freshness = when {
            Lexicon.freshNew.any(t::hasPhrase) -> Freshness.NEW
            Lexicon.freshTrending.any(t::hasPhrase) -> Freshness.TRENDING
            else -> null
        }
        val price = when {
            Lexicon.cheap.any(t::hasPhrase) || t.hasPhrase("cheaper") -> 1
            else -> UNDER_PRICE.find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { amt ->
                when { amt <= 300 -> 1; amt <= 800 -> 2; amt <= 2_000 -> 3; else -> null }
            }
        }
        val openNow = if (Lexicon.openNow.any(t::hasPhrase)) true else null
        // With a category, leftover words are usually a name ("microsoft office", "pvr cinema"): they rank, not filter.
        val residue = residueText(if (categories.isEmpty()) t else forCategories, place?.name ?: unknownPlace)
        val text = dishText ?: residue

        var confidence = when {
            categories.isNotEmpty() -> 0.6f
            text != null -> 0.5f
            near != null || time != null || freshness != null || openNow != null -> 0.4f
            else -> return ListingQuery() to 0f
        }
        if (near != null) confidence += 0.15f
        if (time != null || freshness != null) confidence += 0.1f
        if (radius != null) confidence += 0.1f
        // Every content word is explained by structure: nothing left for an LLM to interpret.
        if (residueText(forCategories, place?.name ?: unknownPlace) == null) confidence = maxOf(confidence, 0.9f)
        return ListingQuery(
            categories = categories,
            text = text,
            near = near,
            time = time,
            freshness = freshness,
            priceLevelMax = price,
            openNow = openNow,
        ) to confidence.coerceAtMost(0.97f)
    }

    /**
     * Fixes typos in category words ("temle", "resturant", "chruch") so a slip of the thumb still finds
     * the right category instead of falling back to a name search. Conservative: only words of 5+
     * letters, one edit (two for 8+ letters), and never words the lexicons already know.
     */
    private fun correctSpelling(t: String): String = t.split(' ').joinToString(" ") { w ->
        if (w.length < 5 || w in KNOWN_TERMS || w in STOP_WORDS || w in REAL_WORDS || w.any(Char::isDigit)) return@joinToString w
        val budget = if (w.length >= 8) 2 else 1
        Lexicon.correctable
            // Typos rarely change the first letter; this keeps "cakes" from becoming "lakes".
            .filter { it[0] == w[0] }
            .map { it to editDistance(w, it) }
            .filter { (_, d) -> d <= budget }
            .minByOrNull { (_, d) -> d }?.first ?: w
    }

    /** Content words not already turned into structure ("nearby" is a location, not a search term). */
    private fun residueText(t: String, placeName: String?): String? {
        val placeWords = placeName?.lowercase()?.split(' ')?.toSet().orEmpty()
        val known: Set<String> = KNOWN_TERMS
        val words = t.split(' ').filter { w ->
            w.length > 2 && w !in STOP_WORDS && w !in placeWords && !w.any(Char::isDigit) && w !in known &&
                Lexicon.timePresets.none { it.first == w } && w !in Lexicon.freshNew
        }
        return words.takeIf { it.isNotEmpty() && it.size <= 4 }?.joinToString(" ")
    }

    private fun toMeters(value: Double, unit: String): Int {
        val meters = when (unit) {
            "m", "meter", "meters", "metre", "metres" -> value
            "mi", "mile", "miles" -> value * 1_609.34
            else -> value * 1_000
        }
        return meters.toInt().coerceIn(GeoFilter.MIN_RADIUS_METERS, GeoFilter.MAX_RADIUS_METERS)
    }

    private companion object {
        val UNKNOWN_PLACE = Regex("""\b(?:in|near|around|at)\s+([a-z][a-z0-9 .'-]{2,40}?)(?=\s+(?:within|this|today|tonight|tomorrow|under|for|with|open|that|which)\b|${'$'})""")
        val NOT_A_PLACE = setOf("me", "here", "my", "us", "the", "area", "town", "city", "night", "evening", "morning", "home", "all", "this", "nearby")

        /** Single words that the lexicons already turn into structure. */
        val KNOWN_TERMS: Set<String> = buildSet {
            (Lexicon.categories.map { it.first } + Lexicon.freshNew + Lexicon.freshTrending + Lexicon.cheap +
                Lexicon.openNow + Lexicon.hereWords + Lexicon.timePresets.map { it.first })
                .forEach { phrase -> addAll(phrase.split(' ')) }
            addAll(listOf("nearby", "near", "within", "around", "under", "below", "metres", "meters", "kms", "km", "nearest", "closest", "upcoming", "latest", "find", "search"))
        }
        val RADIUS = Regex("""(\d+(?:\.\d+)?)\s*(km|kms|kilometers|kilometres|kilometer|kilometre|m|meters|metres|meter|metre|mi|miles|mile)\b""")
        val UNDER_PRICE = Regex("""(?:under|below|less than)\s*(?:₹|rs\.?|inr)?\s*(\d+)""")
        /** Real words one letter away from a category word ("shows" / "shoes"): never "corrected". */
        val REAL_WORDS = setOf(
            "shows", "shops", "store", "stores", "items", "sales", "deals", "offers", "games", "parking", "timing",
            "timings", "price", "prices", "rates", "plans", "pasta", "parts", "parties", "party", "halls", "hall",
        )
        val STOP_WORDS = setOf(
            "show", "me", "any", "find", "what", "whats", "where", "is", "are", "there", "the", "and", "for", "some",
            "something", "good", "best", "places", "place", "want", "can", "get", "please", "with", "near", "around",
            "within", "this", "that", "from", "into", "nice", "cool", "spots", "spot", "things", "thing", "happening",
            "going", "on", "in", "at", "to", "of", "or", "a", "an", "i", "you", "do", "does", "have", "has", "let",
            "lets", "see", "look", "looking", "ones", "one", "open", "now", "far", "away", "km", "kms",
            // Fillers and pleasantries are never search terms.
            "hmm", "hmmm", "umm", "uhh", "okay", "yes", "yeah", "nope", "hello", "hey", "thanks", "thank", "cool", "wow",
        )
    }
}

/** Optimal string alignment distance: insertions, deletions, substitutions and adjacent swaps cost 1. */
internal fun editDistance(a: String, b: String): Int {
    if (kotlin.math.abs(a.length - b.length) > 2) return 3
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
    }
    return d[a.length][b.length]
}

internal fun normalize(raw: String): String = raw.lowercase()
    .replace(Regex("""[^\p{L}\p{N}₹.\s-]"""), " ")
    .replace(Regex("""\.(?!\d)"""), " ")
    .replace(Regex("""\s+"""), " ")
    .trim()

internal fun String.hasPhrase(phrase: String): Boolean = " $this ".contains(" $phrase ")
