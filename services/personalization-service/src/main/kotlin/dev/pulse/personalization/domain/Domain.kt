package dev.pulse.personalization.domain

/**
 * Aggregates locally-DP histograms across devices in a cohort. Laplace noise is zero-mean, so the
 * cohort mean converges to the truth as sqrt(n) while no single upload is trustworthy alone.
 */
class CohortAggregator(private val minDevices: Int = 50) {
    private val sums = mutableMapOf<String, Double>()
    private var devices = 0

    fun add(histogram: Map<String, Double>) {
        histogram.forEach { (k, v) -> sums.merge(k, v, Double::plus) }
        devices++
    }

    /** Null until k-anonymity threshold is reached: small cohorts publish nothing. */
    fun affinities(): Map<String, Double>? =
        if (devices < minDevices) null else sums.mapValues { (_, v) -> v / devices }
}

/** Outbound port: nearest-neighbour search over item embeddings (pgvector `<=>`). */
fun interface SimilarItems {
    fun similarTo(listingId: String, k: Int): List<String>
}
