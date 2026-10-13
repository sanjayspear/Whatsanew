package dev.pulse.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.pulse.core.ai.IntentContext
import dev.pulse.core.ai.IntentInput
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.data.FixedLocationTracker
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ListingResolver
import dev.pulse.core.data.ListingSource
import dev.pulse.core.data.MovementGate
import dev.pulse.core.data.SampleCatalog
import dev.pulse.core.data.asProvider
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.prefs.SqlUserRoleRepository
import dev.pulse.core.prefs.UserRole
import dev.pulse.core.signals.InMemorySignalLog
import dev.pulse.core.signals.LocalRanker
import dev.pulse.dsl.Binding
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.Task
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceAndLocationTest {

    // ---- Issue 4: role persistence ----

    @Test
    fun `role is unset on first launch, then survives an app restart`() {
        val dbFile = File.createTempFile("pulse", ".db").apply { delete(); deleteOnExit() }
        fun open() = PulseDatabase(JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", schema = PulseDatabase.Schema))

        val firstLaunch = SqlUserRoleRepository(open())
        assertNull(firstLaunch.role.value, "first launch must ask")
        firstLaunch.setRole(UserRole.MERCHANT)

        val afterRestart = SqlUserRoleRepository(open()) // new process, same database file
        assertEquals(UserRole.MERCHANT, afterRestart.role.value)
    }

    // ---- Issue 2: location-driven re-query ----

    @Test
    fun `movement under 250 m is ignored, beyond it is accepted`() {
        val gate = MovementGate(250.0)
        assertTrue(gate.offer(GeoCoordinate(13.0466, 77.5126)))
        assertFalse(gate.offer(GeoCoordinate(13.0470, 77.5130)), "~60 m: no re-query")
        assertTrue(gate.offer(GeoCoordinate(13.0500, 77.5126)), "~380 m: re-query")
    }

    @Test
    fun `same near-me query returns different results after the user moves`() {
        val now = 1_770_791_400_000L
        val tracker = FixedLocationTracker(GeoCoordinate(12.9352, 77.6245)) // Koramangala
        val seed = SampleCatalog.listings(now)
        val resolver = ListingResolver(ListingSource { seed }, Gazetteer.Bangalore, tracker.asProvider(), LocalRanker(InMemorySignalLog()), { now })
        val nearMe = Binding(ListingQuery(near = GeoFilter(GeoAnchor.Here, 3_000)), limit = 50)

        val before = resolver.resolve(nearMe).map { it.item.id }.toSet()
        tracker.moveTo(GeoCoordinate(13.0466, 77.5126)) // Dasarahalli, ~16 km away
        val after = resolver.resolve(nearMe).map { it.item.id }.toSet()

        assertTrue(before.isNotEmpty() && after.isNotEmpty())
        assertNotEquals(before, after)
        assertTrue(after.intersect(before).isEmpty())
    }

    // ---- Issue 2: every category, not just food ----

    @Test
    fun `search works across all categories`() {
        val parser = RuleBasedIntentParser(Gazetteer.Bangalore)
        fun cats(q: String) = parser.parse(IntentInput(q), IntentContext()).query!!.categories
        assertEquals(listOf("fashion"), cats("fashion stores near me"))
        assertEquals(listOf("fashion.ethnic"), cats("saree shops in Rajajinagar"))
        assertEquals(listOf("events", "arts", "music", "comedy"), cats("any events this weekend"))
        assertEquals(listOf("fitness"), cats("gyms near Jalahalli"), "gyms span fitness and sports centres in OSM")
        assertEquals(listOf("shopping.mall"), cats("malls nearby"))
        assertEquals(listOf("gems.lake"), cats("a quiet lake to walk"))
    }

    @Test
    fun `place names do not leak into categories`() {
        val f = RuleBasedIntentParser(Gazetteer.Bangalore).parse(IntentInput("theatre near Cubbon Park"), IntentContext())
        assertEquals(listOf("arts.theatre"), f.query!!.categories)
    }

    @Test
    fun `fashion near Dasarahalli finds north-west listings`() {
        val now = 1_770_791_400_000L
        val seed = SampleCatalog.listings(now)
        val resolver = ListingResolver(ListingSource { seed }, Gazetteer.Bangalore, FixedLocationTracker().asProvider(), LocalRanker(InMemorySignalLog()), { now })
        val f = RuleBasedIntentParser(Gazetteer.Bangalore).parse(IntentInput("fashion near Dasarahalli within 6 km"), IntentContext())
        assertEquals(Task.DISCOVER, f.task)
        val titles = resolver.resolve(Binding(f.query!!)).map { it.item.title("en") }
        assertTrue("Metro Fashion Arcade" in titles, titles.toString())
    }

    @Test
    fun `sparse areas are never silently widened (the user is asked instead)`() {
        val now = 1_770_791_400_000L
        val seed = SampleCatalog.listings(now)
        // Middle of nowhere relative to the seed: nothing within 1 km, some within 10 km.
        val tracker = FixedLocationTracker(GeoCoordinate(12.9800, 77.6500))
        val resolver = ListingResolver(ListingSource { seed }, Gazetteer.Bangalore, tracker.asProvider(), LocalRanker(InMemorySignalLog()), { now })
        val near = resolver.resolveWithin(Binding(ListingQuery(listOf("food.cafe"), near = GeoFilter(GeoAnchor.Here, 1_000))))
        assertTrue(near.items.isEmpty() && !near.widened && near.radiusMeters == 1_000)
        val wider = resolver.resolveWithin(Binding(ListingQuery(listOf("food.cafe"), near = GeoFilter(GeoAnchor.Here, 10_000))))
        assertTrue(wider.items.isNotEmpty(), "the offered wider search finds places")
    }
}
