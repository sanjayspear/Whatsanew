package dev.pulse.core

import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ListingResolver
import dev.pulse.core.data.ListingSource
import dev.pulse.core.data.SampleCatalog
import dev.pulse.core.data.TimeWindows
import dev.pulse.core.signals.InMemorySignalLog
import dev.pulse.core.signals.LocalRanker
import dev.pulse.core.signals.Signal
import dev.pulse.dsl.Binding
import dev.pulse.dsl.Freshness
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.RankSpec
import dev.pulse.dsl.RankStrategy
import dev.pulse.dsl.TimePreset
import dev.pulse.dsl.TimeWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RankingAndResolverTest {

    // Wednesday 2026-02-11 12:00 IST
    private val now = 1_770_791_400_000L
    private val signals = InMemorySignalLog()
    private val ranker = LocalRanker(signals)
    private val seed = SampleCatalog.listings(now)
    private val resolver = ListingResolver(ListingSource { seed }, Gazetteer.Bangalore, { GeoCoordinate(12.9352, 77.6245) }, ranker, { now })

    private fun titles(binding: Binding) = resolver.resolve(binding).map { it.item.title("en") }

    @Test
    fun newCafesWithin3kmOfKoramangalaExcludeFarAndOldOnes() {
        val result = titles(
            Binding(ListingQuery(listOf("food.cafe"), near = GeoFilter(GeoAnchor.Place("Koramangala"), 3_000), freshness = Freshness.NEW)),
        )
        assertEquals(setOf("Ember & Bean", "Filter Theory", "Slow Lane Roasters"), result.toSet())
    }

    @Test
    fun theatreThisWeekendExcludesNextMonth() {
        val result = titles(
            Binding(
                ListingQuery(listOf("arts.theatre"), near = GeoFilter(GeoAnchor.Place("Ravindra Kalakshetra")), time = TimeWindow(TimePreset.THIS_WEEKEND)),
                RankSpec(RankStrategy.SOONEST),
            ),
        )
        assertTrue("Yayati — Kannada Repertory" in result)
        assertTrue("Hamlet in Konkani" !in result)
        assertEquals("Midsummer at Cubbon Park", result.first(), "soonest first (Sat 17:30)")
    }

    @Test
    fun weekendWindowOnWednesdayIsSaturdayToMondayIst() {
        val range = TimeWindows.resolve(TimeWindow(TimePreset.THIS_WEEKEND), now, "Asia/Kolkata")!!
        assertEquals(2 * 24 * 60 * 60_000L, range.toMs - range.fromMs)
        assertTrue(range.fromMs > now)
    }

    @Test
    fun attendingADramaInFebruaryBoostsTheatreThenSiblingArts() {
        signals.append(Signal(Signal.Kind.ATTEND, "arts.theatre", "seed-7", atEpochMs = now - 3 * 24 * 60 * 60_000L))
        signals.append(Signal(Signal.Kind.LIKE, "arts.theatre", "seed-7", atEpochMs = now - 3 * 24 * 60 * 60_000L))

        val theatre = ranker.affinity("arts.theatre", now)
        val cinema = ranker.affinity("arts.cinema", now)
        val cafe = ranker.affinity("food.cafe", now)
        assertTrue(theatre > cinema, "same branch beats sibling")
        assertTrue(cinema > cafe, "sibling arts beats unrelated")
        assertEquals(0.0, cafe)
    }

    @Test
    fun personalRankingReordersByAffinity() {
        val anywhere = Binding(ListingQuery(near = GeoFilter(GeoAnchor.Place("Koramangala"), 50_000)), limit = 50)
        val before = titles(anywhere).indexOf("Ray Retrospective: Charulata")
        repeat(3) { signals.append(Signal(Signal.Kind.ATTEND, "arts.cinema", atEpochMs = now - 60_000)) }
        ranker.invalidate()
        val after = titles(anywhere).indexOf("Ray Retrospective: Charulata")
        assertTrue(after < before, "cinema moved up from $before to $after")
    }

    @Test
    fun dismissalsPushCategoriesDown() {
        signals.append(Signal(Signal.Kind.DISMISS, "fitness.yoga", atEpochMs = now))
        assertEquals(0.0, ranker.affinity("fitness.yoga", now))
    }
}
