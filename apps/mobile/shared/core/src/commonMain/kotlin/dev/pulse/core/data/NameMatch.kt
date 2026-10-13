package dev.pulse.core.data

import dev.pulse.core.ai.editDistance

/**
 * How well a place name answers what the user typed, for name searches ("Microsoft", "wellsfargo",
 * "mirosoft"). Spaces, case and punctuation are ignored; small typos are tolerated per word.
 * 0 = no match, higher = better (exact 4, prefix 3, contained 2, fuzzy 1).
 */
object NameMatch {
    fun score(name: String, typed: String): Int {
        val q = typed.lowercase().trim()
        if (q.isEmpty()) return 0
        val n = name.lowercase()
        val qc = q.compact()
        val nc = n.compact()
        if (qc.isEmpty()) return 0
        return when {
            nc == qc -> 4
            nc.startsWith(qc) -> 3
            qc.length >= 3 && nc.contains(qc) -> 2
            fuzzy(n, q) -> 1
            else -> 0
        }
    }

    /** Every typed word is close to some word of the name ("mirosoft" ~ "microsoft", "acenture" ~ "accenture"). */
    private fun fuzzy(name: String, typed: String): Boolean {
        val nameWords = name.split(NON_ALNUM).filter { it.isNotEmpty() }
        val words = typed.split(NON_ALNUM).filter { it.length >= 2 }
        if (words.isEmpty()) return false
        return words.all { w ->
            nameWords.any { nw ->
                nw.startsWith(w) && w.length >= 3 ||
                    w.length >= 5 && nw.length >= 4 && editDistance(w, nw) <= if (w.length >= 8) 2 else 1
            }
        }
    }

    private fun String.compact() = filter { it.isLetterOrDigit() }
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
}
