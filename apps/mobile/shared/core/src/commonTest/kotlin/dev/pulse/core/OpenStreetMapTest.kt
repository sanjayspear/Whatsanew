package dev.pulse.core

import dev.pulse.core.ai.IntentContext
import dev.pulse.core.ai.IntentInput
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.data.FixedLocationTracker
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ListingItem
import dev.pulse.core.data.NearbySync
import dev.pulse.core.data.asProvider
import dev.pulse.core.data.osm.OsmCategories
import dev.pulse.core.data.osm.OverpassClient
import dev.pulse.core.data.osm.PlaceGeocoder
import dev.pulse.dsl.Binding
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.ListingQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OpenStreetMapTest {

    private val fixture = """
        {"elements":[
          {"type":"node","id":1,"lat":12.9277,"lon":77.5156,"version":1,"timestamp":"2026-09-30T10:00:00Z",
           "tags":{"tourism":"hotel","name":"RR Residency","name:kn":"ಆರ್ ಆರ್ ರೆಸಿಡೆನ್ಸಿ","stars":"3","phone":"+91 80 1234 5678"}},
          {"type":"way","id":2,"center":{"lat":12.9300,"lon":77.5170},"version":7,"timestamp":"2026-01-01T00:00:00Z",
           "tags":{"amenity":"restaurant","cuisine":"south_indian","name":"Udupi Grand"}},
          {"type":"node","id":3,"lat":12.93,"lon":77.51,"tags":{"amenity":"bench"}},
          {"type":"node","id":4,"lat":12.93,"lon":77.51,"tags":{"shop":"clothes"}}
        ]}
    """.trimIndent()

    @Test
    fun parsesRealPlacesAndSkipsUnnamedOrUnknownOnes() {
        val items = OverpassClient.parse(fixture)
        assertEquals(listOf("osm:node/1", "osm:way/2"), items.map { it.id })
        val hotel = items[0]
        assertEquals("stay.hotel", hotel.category)
        assertEquals("ಆರ್ ಆರ್ ರೆಸಿಡೆನ್ಸಿ", hotel.titles["kn"])
        assertEquals("+918012345678", hotel.phone)
        assertTrue(hotel.firstListedAtEpochMs > 0, "version-1 elements carry their creation date")
        val restaurant = items[1]
        assertEquals("food.restaurant.south_indian", restaurant.category)
        assertEquals(0L, restaurant.firstListedAtEpochMs, "edited elements have no known opening date")
        assertEquals(12.93, restaurant.lat)
    }

    @Test
    fun queryCoversRequestedCategoriesAroundThePoint() {
        val q = OverpassClient.buildQuery(GeoCoordinate(12.9, 77.5), 3000, OsmCategories.filtersFor(listOf("stay.hotel", "fashion")), 100)
        assertTrue("(around:3000,12.9,77.5)" in q)
        assertTrue("nw[\"tourism\"=\"hotel\"]" in q && "nw[\"tourism\"=\"guest_house\"]" in q, "exact values use the tag index")
        assertTrue("nw[\"shop\"=\"clothes\"]" in q)
        assertTrue("~\"^(" !in q, "no regex value scans")
        assertTrue("[\"name\"]" in q, "only named places are useful as cards")
    }

    @Test
    fun gymsMapToTheGymCategorySoGymSearchesFindThem() {
        assertEquals("fitness.gym", OsmCategories.categoryOf(mapOf("leisure" to "fitness_centre", "name" to "cult.fit")))
    }

    @Test
    fun vagueRequestsSampleEveryRootCategory() {
        val roots = OsmCategories.filtersFor(emptyList()).map { it.first }.toSet()
        assertTrue(listOf("food", "stay", "fashion", "fitness", "shopping", "events").all { it in roots }, roots.toString())
    }

    private val parser = RuleBasedIntentParser(Gazetteer.Bangalore)
    private fun parse(q: String) = parser.parse(IntentInput(q), IntentContext()).query!!

    @Test
    fun hotelsNearRrNagarUsesTheOsmPlacePack() {
        val q = parse("hotels near RR Nagar")
        assertEquals(listOf("stay.hotel"), q.categories)
        assertEquals(GeoAnchor.Place("Rajarajeshwari Nagar"), q.near!!.anchor)
    }

    @Test
    fun longestNeighbourhoodNameWins() {
        assertEquals(GeoAnchor.Place("Jayanagar 4th Block"), parse("events in jayanagar 4th block").near!!.anchor)
        assertEquals(GeoAnchor.Place("Uttarahalli"), parse("gyms near uttarahalli").near!!.anchor)
    }

    @Test
    fun areasOutsideThePackAreKeptForOnlineLookup() {
        val q = parse("cafes near mysuru palace")
        assertEquals(listOf("food.cafe"), q.categories)
        assertEquals(GeoAnchor.Place("Mysuru Palace"), q.near!!.anchor)
        assertEquals(GeoAnchor.Here, parse("pharmacy near me").near!!.anchor)
    }

    // ---- Sync: fetch once, cache, degrade gracefully ----

    private fun client(status: HttpStatusCode, body: String, onRequest: () -> Unit = {}) = HttpClient(MockEngine {
        onRequest()
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    })

    @Test
    fun syncFetchesOnceThenServesFromCache() = runTest {
        var requests = 0
        val stored = mutableListOf<ListingItem>()
        val http = client(HttpStatusCode.OK, fixture) { requests++ }
        val sync = NearbySync(OverpassClient(http, listOf("https://test")), PlaceGeocoder(http), { stored += it },
            FixedLocationTracker(GeoCoordinate(12.9277, 77.5156)).asProvider(), { 0L })
        val nearMe = Binding(ListingQuery(listOf("stay.hotel"), near = GeoFilter(GeoAnchor.Here, 3_000)))

        sync.ensure(nearMe)
        sync.ensure(nearMe)
        assertEquals(1, requests, "second call within 6 h is served from cache")
        assertEquals(2, stored.size)
        assertEquals(1, sync.version.value)
        assertNull(sync.lastError.value)
    }

    @Test
    fun allServersDownLeavesAFriendlyNoticeAndNoCrash() = runTest {
        val http = client(HttpStatusCode.TooManyRequests, "<html>rate limited</html>")
        val sync = NearbySync(OverpassClient(http, listOf("https://a", "https://b")), PlaceGeocoder(http), {},
            FixedLocationTracker(GeoCoordinate(12.9, 77.5)).asProvider(), { 0L })
        sync.ensure(Binding(ListingQuery(near = GeoFilter(GeoAnchor.Here))))
        assertNotNull(sync.lastError.value)
        assertEquals(0, sync.version.value)
    }
}
