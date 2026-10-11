package dev.pulse.core.data

import dev.pulse.dsl.TimePreset
import dev.pulse.dsl.TimeWindow
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Absolute `[from, to)` in epoch ms. */
data class EpochRange(val fromMs: Long, val toMs: Long)

/**
 * Resolves symbolic presets in the *venue's* zone. "This weekend" in Bangalore and in London
 * are different instants, and the DSL never needs to know.
 */
object TimeWindows {

    fun startOfDay(nowMs: Long, zoneId: String): Long {
        val tz = TimeZone.of(zoneId)
        return Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    fun resolve(window: TimeWindow, nowMs: Long, zoneId: String): EpochRange? {
        if (window.fromEpochMs != null || window.toEpochMs != null) {
            return EpochRange(window.fromEpochMs ?: nowMs, window.toEpochMs ?: Long.MAX_VALUE)
        }
        val preset = window.preset ?: return null
        val tz = TimeZone.of(zoneId)
        val now = Instant.fromEpochMilliseconds(nowMs)
        val today = now.toLocalDateTime(tz).date
        fun startOf(daysFromToday: Int) = today.plus(daysFromToday, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()

        return when (preset) {
            TimePreset.NOW -> EpochRange(nowMs, nowMs + 60 * 60_000)
            TimePreset.TODAY -> EpochRange(nowMs, startOf(1))
            TimePreset.TONIGHT -> EpochRange(
                maxOf(nowMs, today.atTime(LocalTime(17, 0)).toInstant(tz).toEpochMilliseconds()),
                startOf(1) + 3 * 60 * 60_000, // until 3am
            )
            TimePreset.TOMORROW -> EpochRange(startOf(1), startOf(2))
            TimePreset.NEXT_7_DAYS -> EpochRange(nowMs, startOf(7))
            TimePreset.THIS_WEEKEND -> {
                val dow = today.dayOfWeek
                when (dow) {
                    DayOfWeek.SATURDAY -> EpochRange(nowMs, startOf(2))
                    DayOfWeek.SUNDAY -> EpochRange(nowMs, startOf(1))
                    else -> {
                        val toSaturday = DayOfWeek.SATURDAY.isoDayNumber - dow.isoDayNumber
                        EpochRange(startOf(toSaturday), startOf(toSaturday + 2))
                    }
                }
            }
        }
    }
}
