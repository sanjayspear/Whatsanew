package dev.pulse.core.live

import dev.pulse.core.ai.Lexicon
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.osm.OverpassClient
import dev.pulse.core.platform.createHttpClient
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** Opt-in live check against public OpenStreetMap servers: PULSE_LIVE=1. Never runs in CI by default. */
class LiveOsmProbe {
    @Test
    fun probe() = runBlocking {
        if (System.getenv("PULSE_LIVE") != "1") return@runBlocking
        val client = OverpassClient(createHttpClient())
        val cases = listOf(
            Triple("gyms 2 km near phone", GeoCoordinate(12.897455, 77.529226), 2000 to listOf("fitness.gym")),
            Triple("gyms 4 km near phone", GeoCoordinate(12.897455, 77.529226), 4000 to listOf("fitness.gym")),
            Triple("events 5 km Jayanagar", GeoCoordinate(12.9308, 77.5838), 5000 to Lexicon.EVENTS),
            Triple("events 3 km Jayanagar", GeoCoordinate(12.9308, 77.5838), 3000 to Lexicon.EVENTS),
        )
        for ((label, c, rc) in cases) {
            val t0 = System.currentTimeMillis()
            val r = runCatching { client.nearby(c, rc.first, rc.second) }
            println("PROBE $label -> ${r.map { "${it.size}: ${it.take(4).map { p -> p.titles["en"] }}" }.getOrElse { "ERROR ${it.message} | ${it.cause?.message}" }} in ${System.currentTimeMillis() - t0} ms")
        }
    }
}
