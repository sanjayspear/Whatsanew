package dev.pulse.core.signals

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sign
import kotlin.random.Random

/** Strips PII from any free text before it is stored for upload (queries, refinements). */
object PiiRedactor {
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val PHONE = Regex("""(?:\+?\d[\s-]?){8,14}\d""")
    private val CARD = Regex("""\b(?:\d[ -]?){13,19}\b""")
    private val AADHAAR = Regex("""\b\d{4}\s?\d{4}\s?\d{4}\b""")

    fun redact(text: String): String = text
        .replace(EMAIL, "[email]")
        .replace(CARD, "[number]")
        .replace(AADHAAR, "[id]")
        .replace(PHONE, "[phone]")
}

/**
 * Local differential privacy for uploaded aggregates. The device sends a per-category
 * engagement histogram with Laplace noise (sensitivity is clipped per batch), never raw events or
 * listing ids. The server can learn "theatre is hot in Basavanagudi" across many users, but
 * cannot learn what any one user did.
 */
class LocalDifferentialPrivacy(
    private val epsilon: Double = 2.0,
    private val clipPerCategory: Double = 10.0,
    private val random: Random = Random.Default,
) {
    fun privatize(histogram: Map<String, Double>): Map<String, Double> {
        val scale = clipPerCategory / epsilon
        return histogram.mapValues { (_, v) -> v.coerceIn(-clipPerCategory, clipPerCategory) + laplace(scale) }
    }

    private fun laplace(scale: Double): Double {
        val u = random.nextDouble() - 0.5
        return -scale * sign(u) * ln(1 - 2 * abs(u))
    }

    companion object {
        /** Root-level only: "arts", "food". Coarser categories mean less identifying uploads. */
        fun histogramOf(signals: List<Signal>): Map<String, Double> = signals
            .groupBy { it.category.substringBefore('.') }
            .mapValues { (_, list) -> list.sumOf { it.kind.weight * if (it.kind == Signal.Kind.DWELL) it.value.toDouble().coerceAtMost(60.0) else 1.0 } }
    }
}
