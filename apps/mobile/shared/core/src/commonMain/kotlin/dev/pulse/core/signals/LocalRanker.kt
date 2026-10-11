package dev.pulse.core.signals

import dev.pulse.core.data.ResolvedItem
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * On-device personal ranker: affinity x proximity x freshness x quality.
 *
 * Affinity is built from the local signal log with exponential time decay and *taxonomy-aware
 * generalisation*. Attending a Kannada play at Ravindra Kalakshetra in February boosts all of
 * `arts.theatre` strongly, sibling `arts.*` (cinema, galleries) moderately, and nothing else.
 * That's the "liked a drama -> surface similar theatre, festivals, cinema" behaviour, offline.
 *
 * Phase 4 replaces the hand-set weights with a distilled model shipped via model-registry. The
 * interface stays the same.
 */
class LocalRanker(
    private val signals: SignalLog,
    private val weights: Weights = Weights(),
) {
    data class Weights(
        val affinity: Double = 0.40,
        val proximity: Double = 0.30,
        val freshness: Double = 0.10,
        val quality: Double = 0.20,
        val halfLifeDays: Double = 45.0,
        val historyDays: Long = 365,
    )

    private var cacheKey: Long = -1
    private var cachedSignals: List<Signal> = emptyList()

    fun score(r: ResolvedItem, radiusMeters: Int, nowMs: Long): Double {
        val proximity = 1.0 - min(1.0, r.distanceMeters / radiusMeters.coerceAtLeast(1))
        val ageDays = (nowMs - r.item.firstListedAtEpochMs) / DAY_MS.toDouble()
        val freshness = exp(-ageDays / 120.0)
        val quality = ((r.item.rating ?: 3.5f) - 3.0) / 2.0
        return weights.affinity * affinity(r.item.category, nowMs) +
            weights.proximity * proximity +
            weights.freshness * freshness +
            weights.quality * quality.coerceIn(0.0, 1.0)
    }

    /** 0..1 saturating affinity for [category]. */
    fun affinity(category: String, nowMs: Long): Double {
        val lambda = ln(2.0) / (weights.halfLifeDays * DAY_MS)
        val raw = recentSignals(nowMs).sumOf { s ->
            val similarity = similarity(s.category, category)
            if (similarity == 0.0) return@sumOf 0.0
            val amount = if (s.kind == Signal.Kind.DWELL) min(s.value.toDouble(), 60.0) else 1.0
            s.kind.weight * amount * similarity * exp(-lambda * (nowMs - s.atEpochMs))
        }
        return if (raw <= 0) 0.0 else 1.0 - exp(-raw / 4.0)
    }

    private fun recentSignals(nowMs: Long): List<Signal> {
        val bucket = nowMs / (60_000) // refresh at most once per minute
        if (bucket != cacheKey) {
            cachedSignals = signals.since(nowMs - weights.historyDays * DAY_MS)
            cacheKey = bucket
        }
        return cachedSignals
    }

    fun invalidate() {
        cacheKey = -1
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60_000

        /** 1.0 same branch, 0.4 siblings under the same root, 0 otherwise. */
        fun similarity(signalCategory: String, candidate: String): Double = when {
            candidate == signalCategory || candidate.startsWith("$signalCategory.") || signalCategory.startsWith("$candidate.") -> 1.0
            signalCategory.substringBefore('.') == candidate.substringBefore('.') -> 0.4
            else -> 0.0
        }
    }
}
