package dev.pulse.core.signals

import kotlinx.serialization.Serializable

/** One interaction. Stored locally; only privatized aggregates ever leave the device. */
@Serializable
data class Signal(
    val kind: Kind,
    val category: String,
    val listingId: String? = null,
    val atEpochMs: Long,
    /** Dwell seconds for [Kind.DWELL]; otherwise unused. */
    val value: Float = 1f,
) {
    @Serializable
    enum class Kind(val weight: Double) {
        IMPRESSION(0.0),
        TAP(1.0),
        DWELL(0.1),            // per second, capped by the ranker
        SAVE(3.0),
        LIKE(3.0),
        ATTEND(5.0),           // booked / checked in: the strongest signal
        REFINE(0.5),
        DISMISS(-2.0),
    }
}

interface SignalLog {
    fun append(signal: Signal)
    fun since(epochMs: Long): List<Signal>
    fun pendingUpload(limit: Int): List<Pair<Long, Signal>>
    fun markUploaded(ids: List<Long>)
}

class InMemorySignalLog : SignalLog {
    private val rows = mutableListOf<Triple<Long, Signal, Boolean>>()
    private var nextId = 1L

    override fun append(signal: Signal) {
        rows += Triple(nextId++, signal, false)
    }

    override fun since(epochMs: Long) = rows.filter { it.second.atEpochMs >= epochMs }.map { it.second }

    override fun pendingUpload(limit: Int) = rows.filter { !it.third }.take(limit).map { it.first to it.second }

    override fun markUploaded(ids: List<Long>) {
        val set = ids.toSet()
        for (i in rows.indices) if (rows[i].first in set) rows[i] = rows[i].copy(third = true)
    }
}
