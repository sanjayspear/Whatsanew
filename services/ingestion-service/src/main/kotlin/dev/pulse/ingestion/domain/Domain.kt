package dev.pulse.ingestion.domain

data class SignalBatch(
    val batchId: String,
    val deviceCohort: String,
    val modelVersion: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val histogram: Map<String, Double>,
    val epsilon: Double,
)

sealed interface Verdict {
    data object Accepted : Verdict
    data object Duplicate : Verdict
    data class Rejected(val reason: String) : Verdict
}

/** Outbound ports. */
fun interface IdempotencyStore { fun firstSeen(batchId: String): Boolean }
fun interface SignalSink { fun publish(batch: SignalBatch) }

/** Defensive intake: the server never trusts the client's privacy claims blindly. */
class AcceptBatch(
    private val seen: IdempotencyStore,
    private val sink: SignalSink,
    private val maxEpsilon: Double = 4.0,
    private val maxCategories: Int = 64,
) {
    fun accept(batch: SignalBatch, nowMs: Long): Verdict {
        if (batch.epsilon <= 0 || batch.epsilon > maxEpsilon) return Verdict.Rejected("epsilon outside (0, $maxEpsilon]")
        if (batch.histogram.size > maxCategories) return Verdict.Rejected("too many categories")
        if (batch.histogram.keys.any { '.' in it }) return Verdict.Rejected("only root categories are accepted")
        if (batch.windowEndMs < batch.windowStartMs || batch.windowEndMs > nowMs + 5 * 60_000) return Verdict.Rejected("bad window")
        if (!seen.firstSeen(batch.batchId)) return Verdict.Duplicate
        sink.publish(batch)
        return Verdict.Accepted
    }
}
