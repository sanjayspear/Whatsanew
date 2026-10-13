package dev.pulse.core.ai

/**
 * Suggestions while typing: past searches, category completions (typo-tolerant), and names of real
 * places nearby, so an exact business / venue name is one tap away. Pure; the caller supplies names.
 */
object SearchSuggestions {
    fun complete(typed: String, history: List<String>, placeNames: List<String>, max: Int = 6): List<String> {
        val t = typed.trim().lowercase()
        if (t.length < 2) return emptyList()
        val out = LinkedHashSet<String>()

        history.filter { it.lowercase().startsWith(t) || (t.length >= 3 && it.lowercase().contains(t)) }
            .take(2).forEach(out::add)

        // Complete (or correct) the word being typed into a category phrase: "tem" -> "temples near me".
        val head = t.substringBeforeLast(' ', "").trim()
        val last = t.substringAfterLast(' ')
        if (last.length >= 2) {
            Lexicon.categories.asSequence()
                .map { it.first }
                .filter { p -> p.startsWith(last) || (last.length >= 4 && p.length >= 4 && editDistance(last, p.take(last.length + 1)) <= 1 && p[0] == last[0]) }
                .sortedBy { p -> (if (p.startsWith(last)) 0 else 1) * 100 + p.length }
                .take(3)
                .forEach { p -> out += listOf(head, p).filter(String::isNotEmpty).joinToString(" ") + if (head.isEmpty() && "near" !in p) " near me" else "" }
        }

        // Exact names first, then names that start with it, then names containing every typed word.
        placeNames.asSequence().distinct()
            .mapNotNull { n -> dev.pulse.core.data.NameMatch.score(n, t).takeIf { it > 0 && (t.length >= 3 || it >= 3) }?.let { -it to n } }
            .sortedWith(compareBy({ it.first }, { it.second.length }))
            .take(3)
            .forEach { out += it.second }

        return out.filterNot { it.equals(typed.trim(), ignoreCase = true) }.take(max)
    }
}
