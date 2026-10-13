package dev.pulse.core.ai

/** A question for the user instead of a guess: each option is a rewritten, unambiguous request. */
data class Clarification(val question: String, val options: List<Option>) {
    data class Option(val label: String, val utterance: String)
}

/**
 * Asks before searching when a request could mean clearly different things ("hotels" in Indian
 * English can be a place to stay or a place to eat; "shops" alone names no kind of shop). Returning
 * unrelated results is worse than one tap.
 */
object Clarifier {
    fun question(text: String): Clarification? {
        val t = normalize(text)
        if (t.isBlank()) return null
        hotel(t)?.let { return it }
        return shops(t)
    }

    private fun hotel(t: String): Clarification? {
        val word = listOf("hotels", "hotel").firstOrNull(t::hasPhrase) ?: return null
        val stay = listOf("stay", "room", "rooms", "lodge", "lodging", "night", "nights", "check in", "resort", "star")
        val eat = listOf("eat", "food", "meals", "lunch", "dinner", "breakfast", "biryani", "dosa", "veg", "non veg", "restaurant")
        if (stay.any(t::hasPhrase) || eat.any(t::hasPhrase)) return null
        return Clarification(
            "Do you mean a place to stay or a place to eat?",
            listOf(
                Clarification.Option("A place to stay", t.replacePhrase(word, "hotels to stay")),
                Clarification.Option("A place to eat", t.replacePhrase(word, "restaurants")),
            ),
        )
    }

    private fun shops(t: String): Clarification? {
        val word = listOf("shops", "shop", "stores", "store").firstOrNull(t::hasPhrase) ?: return null
        // "juice shop", "pet store" etc. already name the kind of shop.
        val withoutWord = " $t ".replace(" $word ", " ").trim().replace(PLACE_TAIL, "").trim()
        if (Lexicon.categories.any { withoutWord.hasPhrase(it.first) }) return null
        if (withoutWord.split(' ').count { it.length > 2 && it !in GENERIC } > 0) return null
        return Clarification(
            "What kind of shop are you looking for?",
            listOf(
                "Grocery" to "grocery shops", "Clothes" to "clothes shops", "Electronics" to "electronics shops",
                "Pharmacy" to "pharmacies", "Books" to "bookstores", "Juice" to "juice shops",
            ).map { (label, kind) -> Clarification.Option(label, t.replacePhrase(word, kind)) },
        )
    }

    /** "... in Whitefield" / "near MG Road": the place part says where, not what. */
    private val PLACE_TAIL = Regex("""\b(in|near|around|at)\s+.*$""")

    private val GENERIC = setOf("near", "nearby", "around", "find", "show", "any", "the", "some", "me", "best", "good", "within", "open", "now") +
        Lexicon.hereWords.flatMap { it.split(' ') }

    private fun String.replacePhrase(phrase: String, with: String) = " $this ".replace(" $phrase ", " $with ").trim()
}
