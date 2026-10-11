package dev.pulse.notification.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Respect the *recipient's* local night, wherever they are. */
data class QuietHours(val start: LocalTime = LocalTime.of(22, 0), val end: LocalTime = LocalTime.of(8, 0)) {
    fun isQuiet(at: Instant, zone: ZoneId): Boolean {
        val t = at.atZone(zone).toLocalTime()
        return if (start > end) t >= start || t < end else t >= start && t < end
    }
}

/** Outbound port: FCM / APNs adapters. */
fun interface PushGateway {
    fun send(deviceToken: String, title: String, body: String, deepLink: String): Boolean
}
