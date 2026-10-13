package dev.pulse.core

import dev.pulse.core.ai.Clarifier
import dev.pulse.core.ai.IntentContext
import dev.pulse.core.ai.IntentInput
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.ai.SearchSuggestions
import dev.pulse.core.ai.TieredAiEngine
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ListingItem
import dev.pulse.core.data.ListingResolver
import dev.pulse.core.data.ListingSource
import dev.pulse.core.data.LocationProvider
import dev.pulse.core.data.MerchantDraft
import dev.pulse.core.data.MerchantPosts
import dev.pulse.core.data.OfferingType
import dev.pulse.core.data.SearchDefaults
import dev.pulse.core.data.booking.BookingDestinations
import dev.pulse.core.data.osm.OsmCategories
import dev.pulse.core.pipeline.IntentPipeline
import dev.pulse.core.prefs.AppSettingsStore
import dev.pulse.core.prefs.InMemoryKeyValueStore
import dev.pulse.core.prefs.InMemoryProfileStore
import dev.pulse.core.prefs.ProfileRules
import dev.pulse.core.prefs.SearchHistory
import dev.pulse.core.signals.InMemorySignalLog
import dev.pulse.core.signals.LocalRanker
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.Binding
import dev.pulse.dsl.DslValidator
import dev.pulse.dsl.Freshness
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.Roles
import dev.pulse.dsl.SurfaceComposer
import dev.pulse.dsl.walk
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The "Search Around" functional requirements (Oct 2026), one test per rule where it can be checked on-device. */
class SearchAroundRequirementsTest {

    private val parser = RuleBasedIntentParser(Gazetteer.Bangalore)
    private fun categories(text: String) = parser.parse(IntentInput(text), IntentContext()).query?.categories.orEmpty()

    @AfterTest
    fun resetDefaults() {
        SearchDefaults.radiusMeters = GeoFilter.DEFAULT_RADIUS_METERS
    }

    // ---- 2.1 Default radius 6 km ----

    @Test
    fun defaultRadiusIsSixKilometres() {
        assertEquals(6_000, GeoFilter.DEFAULT_RADIUS_METERS)
        val near = parser.parse(IntentInput("temples near me"), IntentContext()).query!!.near!!
        assertEquals(GeoAnchor.Here, near.anchor)
        assertEquals(6_000, near.radiusMeters)
    }

    @Test
    fun searchesWithoutAPlaceUseTheDefaultRadius() = runTest {
        val p = pipeline()
        p.submit(IntentInput("salons"))
        val bind = p.state.value.surface!!.root.children.first { it.role == Roles.RESULTS }.bind!!
        assertEquals(GeoFilter(GeoAnchor.Here, 6_000), bind.query.near)
    }

    @Test
    fun theDefaultRadiusFollowsSettings() {
        val settings = AppSettingsStore(InMemoryKeyValueStore(), Gazetteer.Bangalore)
        settings.setDefaultRadiusKm(10)
        assertEquals(10_000, parser.parse(IntentInput("temples near me"), IntentContext()).query!!.near!!.radiusMeters)
        assertEquals(3_000, parser.parse(IntentInput("temples within 3 km"), IntentContext()).query!!.near!!.radiusMeters, "an explicit distance wins")
    }

    @Test
    fun aChosenAreaReplacesTheGpsFix() {
        val settings = AppSettingsStore(InMemoryKeyValueStore(), Gazetteer.Bangalore)
        assertTrue(settings.setManualArea("Whitefield"))
        assertEquals("Whitefield", settings.settings.value.manualArea?.name)
        assertFalse(settings.setManualArea("Atlantis"), "unknown areas are refused, not guessed")
        assertTrue(settings.setManualArea(null))
        assertNull(settings.manualCoordinate())
    }

    // ---- 2.2 Never widen silently ----

    @Test
    fun resolverNeverWidensOnItsOwn() {
        val far = item("far", "worship.temple", 12.9716 + 0.09, 77.5946) // ~10 km north
        val resolver = resolver(listOf(far))
        val r = resolver.resolveWithin(Binding(ListingQuery(listOf("worship.temple"), near = GeoFilter(GeoAnchor.Here, 6_000))))
        assertTrue(r.items.isEmpty())
        assertFalse(r.widened)
        assertEquals(1, resolver.resolveWithin(Binding(ListingQuery(listOf("worship.temple"), near = GeoFilter(GeoAnchor.Here, 15_000)))).items.size)
    }

    // ---- 2.3 Search accuracy: categories, not names ----

    @Test
    fun spokenCategoriesMapToTheRightKindOfPlace() {
        assertEquals(listOf("worship.temple"), categories("find temples near me"))
        assertEquals(listOf("worship.church"), categories("church near me"))
        assertEquals(listOf("worship.mosque"), categories("mosque"))
        assertEquals(listOf("beauty.salon"), categories("salons in Whitefield"))
        assertEquals(listOf("food.juice"), categories("search for fresh juice shops within 3 km"))
        assertEquals(listOf("business.it_park"), categories("find the nearest IT park"))
        assertEquals(listOf("worship.jain"), categories("jain temple"), "not also a generic temple")
    }

    @Test
    fun nearbyIsNeverReadAsAPlaceName() {
        val q = parser.parse(IntentInput("movies in nearby cinemas"), IntentContext()).query!!
        assertEquals(listOf("arts.cinema"), q.categories)
        assertTrue(q.near?.anchor !is GeoAnchor.Place, "was ${q.near}")
        val f = parser.parse(IntentInput("find the nearest IT park"), IntentContext())
        assertTrue(f.confidence >= 0.9f, "fully understood, so the on-device model isn't woken")
    }

    @Test
    fun anItParkIsNotAPark() {
        assertEquals(listOf("business.it_park"), categories("tech parks near me"))
    }

    @Test
    fun typosStillFindTheCategory() {
        assertEquals(listOf("worship.temple"), categories("temle near me"))
        assertEquals(listOf("food.restaurant"), categories("resturant"))
        assertEquals(listOf("worship.church"), categories("chruch"))
        assertEquals(listOf("food.bakery"), categories("cakes"), "a real word is never 'corrected' into cafes or lakes")
        assertEquals(emptyList(), categories("pasta"), "nor is pasta 'corrected' into anything")
        assertTrue(categories("shows near me").none { it.startsWith("fashion") }, "shows are not shoes")
    }

    @Test
    fun placeTypeComesFromOsmTagsNeverTheName() {
        assertEquals("food.cafe", OsmCategories.categoryOf(mapOf("amenity" to "cafe", "name" to "Temple Tree Cafe")))
        assertEquals("worship.temple", OsmCategories.categoryOf(mapOf("amenity" to "place_of_worship", "religion" to "hindu", "name" to "Sri Rama Mandira")))
        assertEquals("worship.church", OsmCategories.categoryOf(mapOf("amenity" to "place_of_worship", "religion" to "christian", "name" to "St. Mark's")))
        assertEquals("food.juice", OsmCategories.categoryOf(mapOf("amenity" to "cafe", "name" to "Fresh Juice Corner")))
        assertEquals("beauty.salon", OsmCategories.categoryOf(mapOf("shop" to "hairdresser", "name" to "Green Trends")))
        assertEquals("business.it_park", OsmCategories.categoryOf(mapOf("landuse" to "commercial", "name" to "Manyata Tech Park")))
        assertNull(OsmCategories.categoryOf(mapOf("landuse" to "commercial", "name" to "Some Plot")))
    }

    @Test
    fun aTempleSearchReturnsTemplesNotShopsNamedTemple() {
        val temple = item("t", "worship.temple", 12.9716, 77.5946, "Sri Venkateshwara Temple")
        val shop = item("s", "food.restaurant", 12.9717, 77.5946, "Temple Meals")
        val q = parser.parse(IntentInput("temples near me"), IntentContext()).query!!
        assertEquals(listOf("t"), resolver(listOf(temple, shop)).resolve(Binding(q)).map { it.item.id })
    }

    @Test
    fun exactNamesComeFirst() {
        val a = item("a", "shopping", 12.9716, 77.5946, "Orion Mall Parking")
        val b = item("b", "shopping.mall", 12.9800, 77.5946, "Orion Mall")
        val q = ListingQuery(text = "orion mall", near = GeoFilter(GeoAnchor.Here, 6_000))
        assertEquals("b", resolver(listOf(a, b)).resolve(Binding(q)).first().item.id)
    }

    // ---- 2.4 Search by company / business name ----

    @Test
    fun companyNamesMatchDespiteSpacingAndTypos() {
        assertEquals(4, dev.pulse.core.data.NameMatch.score("Wells Fargo", "wellsfargo"))
        assertTrue(dev.pulse.core.data.NameMatch.score("Microsoft", "mirosoft") > 0)
        assertTrue(dev.pulse.core.data.NameMatch.score("Microsoft Research and Development", "microsoft") >= 3)
        assertTrue(dev.pulse.core.data.NameMatch.score("Accenture", "acenture") > 0)
        assertEquals(0, dev.pulse.core.data.NameMatch.score("Micro Mart", "mirosoft"))
    }

    @Test
    fun searchingACompanyFindsItsOffices() {
        val ms = item("ms", "business.office", 12.9716, 77.5946, "Microsoft")
        val wf = item("wf", "business.office", 12.9720, 77.5950, "Wells Fargo")
        val shop = item("x", "shopping", 12.9716, 77.5946, "Micro Mart")
        val r = resolver(listOf(ms, wf, shop))
        for ((typed, id) in listOf("microsoft" to "ms", "mirosoft" to "ms", "wellsfargo" to "wf", "wells fargo near me" to "wf")) {
            val q = parser.parse(IntentInput(typed), IntentContext()).query!!
            assertEquals(listOf(id), r.resolve(Binding(q.copy(near = q.near ?: GeoFilter(GeoAnchor.Here, 6_000)))).map { it.item.id }, typed)
        }
        // "microsoft office": the offices category, with Microsoft first.
        val q = parser.parse(IntentInput("microsoft office"), IntentContext()).query!!
        assertEquals(listOf("business.office"), q.categories)
        assertEquals("ms", r.resolve(Binding(q.copy(near = GeoFilter(GeoAnchor.Here, 6_000)))).first().item.id)
    }

    @Test
    fun theModelCannotTurnACompanyNameIntoACategory() {
        dev.pulse.core.data.NameIndex.knows = { it == "microsoft" }
        try {
            val rules = parser.parse(IntentInput("microsoft"), IntentContext())
            val llm = rules.copy(query = ListingQuery(categories = listOf("food.cafe")), source = dev.pulse.dsl.FrameSource.ON_DEVICE_LLM)
            val merged = dev.pulse.core.ai.FrameMerge.merge(rules, llm) { false }
            assertEquals(emptyList(), merged.query!!.categories)
            assertEquals("microsoft", merged.query!!.text)
        } finally {
            dev.pulse.core.data.NameIndex.knows = { false }
        }
    }

    @Test
    fun theNearestMatchBeyondTheRadiusIsReported() {
        val far = item("ms", "business.office", 12.9716 + 0.08, 77.5946, "Microsoft") // ~9 km
        val near = resolver(listOf(far)).nearestBeyond(Binding(ListingQuery(text = "microsoft", near = GeoFilter(GeoAnchor.Here, 6_000))))
        assertEquals("ms", near?.item?.id)
        assertTrue(near!!.distanceMeters in 8_000.0..10_000.0)
    }

    @Test
    fun onlineNameSearchResultsBecomePlaces() {
        val body = """{"features":[
          {"geometry":{"coordinates":[77.62,12.98]},"properties":{"osm_type":"N","osm_id":42,"osm_key":"office","osm_value":"company","name":"Microsoft","street":"Outer Ring Road","city":"Bengaluru"}},
          {"geometry":{"coordinates":[77.6,12.9]},"properties":{"osm_type":"W","osm_id":7,"osm_key":"highway","osm_value":"residential","name":"Microsoft Road"}}
        ]}"""
        val items = dev.pulse.core.data.osm.PhotonClient.parse(body)
        assertEquals(1, items.size, "streets are not places")
        assertEquals("osm:node/42", items[0].id)
        assertEquals("business.office", items[0].category)
        assertEquals("Outer Ring Road, Bengaluru", items[0].address)
    }

    @Test
    fun ambiguousRequestsAskFirst() {
        val hotel = assertNotNull(Clarifier.question("hotels near me"))
        assertEquals(listOf("hotels to stay near me", "restaurants near me"), hotel.options.map { it.utterance })
        assertNull(Clarifier.question("hotels to stay in Koramangala"))
        assertNotNull(Clarifier.question("shops near me"))
        assertNotNull(Clarifier.question("shops in Whitefield"))
        assertNull(Clarifier.question("juice shops"))
        assertNull(Clarifier.question("temples near me"))
        // Every option is itself a clear request.
        hotel.options.forEach { assertNull(Clarifier.question(it.utterance)) }
    }

    @Test
    fun theClarifyingQuestionReplacesAGuess() = runTest {
        val p = pipeline()
        p.submit(IntentInput("hotels near me"))
        val root = p.state.value.surface!!.root
        assertEquals(listOf(IntentPipeline.CLARIFY_COMPONENT), root.children.map { it.component })
    }

    // ---- 3. Booking ----

    @Test
    fun bookingNeedsARealDestination() {
        val cinema = item("c", "arts.cinema", 12.9716, 77.5946, "PVR Orion")
        val cafe = item("f", "food.cafe", 12.9716, 77.5946, "Brew")
        assertTrue(BookingDestinations.isBookable(cinema))
        assertFalse(BookingDestinations.isBookable(cafe))
        assertEquals("c", cinema.field("bookable"))
        assertNull(cafe.field("bookable"))
        val urls = BookingDestinations.optionsFor(cinema).map { it.url }
        assertEquals(listOf("https://in.bookmyshow.com/explore/movies-bengaluru", "https://www.district.in/movies/"), urls)
        assertTrue(BookingDestinations.isBookable(cafe.copy(bookingUrl = "https://example.org/tickets")), "a merchant's own link counts")
    }

    @Test
    fun unsafeLinksAreNeverOffered() {
        listOf("http://x.com", "javascript:alert(1)", "intent://x", "https://", "https://nodot", "ftp://a.b").forEach {
            assertFalse(BookingDestinations.isSafeLink(it), it)
        }
        val theatre = item("t", "arts.theatre", 12.9716, 77.5946, "Ranga Shankara").copy(website = "http://insecure.example")
        assertTrue(BookingDestinations.optionsFor(theatre).none { it.url.startsWith("http://") })
    }

    @Test
    fun cardsAlwaysCarryBookButOnlyBookableItemsResolveIt() {
        val surface = SurfaceComposer { "s" }.compose(
            dev.pulse.dsl.IntentFrame(dev.pulse.dsl.Task.DISCOVER, query = ListingQuery(listOf("food.cafe"))), null, dev.pulse.dsl.LayoutPrefs(),
        ).patch.ops.filterIsInstance<dev.pulse.dsl.UiOp.ReplaceSurface>().single().surface
        val card = surface.root.walk().first { it.bind?.template != null }.bind!!.template!!
        assertEquals(ActionKind.BOOK, card.actions.first().kind)
        assertEquals("\$item.bookable", card.actions.first().args["bookable"])
    }

    // ---- 5. Profile ----

    @Test
    fun onlyTheNameIsRequiredAndPhonesAreNormalised() {
        assertNotNull(ProfileRules.nameProblem("  "))
        assertNull(ProfileRules.nameProblem("Sanjay"))
        assertEquals(null, ProfileRules.normalizePhone("").getOrThrow())
        assertEquals("+919876543210", ProfileRules.normalizePhone("98765 43210").getOrThrow())
        assertEquals("+919876543210", ProfileRules.normalizePhone("+91 98765-43210").getOrThrow())
        assertTrue(ProfileRules.normalizePhone("12345").isFailure)
    }

    // ---- 6. Merchant posts ----

    private val now = 1_792_000_000_000L // 2026-10-15

    private fun merchant(kv: InMemoryKeyValueStore = InMemoryKeyValueStore(), cache: MutableMap<String, ListingItem> = mutableMapOf()) =
        MerchantPosts(kv, Gazetteer.Bangalore, { now }, { items -> items.forEach { cache[it.id] = it } }, { cache.remove(it) }, { GeoCoordinate(12.97, 77.59) })

    private val goodDraft = MerchantDraft(
        title = "Grand opening: Juice Junction", type = OfferingType.SHOP, description = "Fresh juices and smoothies, 20% off this week",
        area = "Indiranagar", link = "https://juicejunction.example/offer",
    )

    @Test
    fun aValidPostIsPublishedIntoSearch() {
        val cache = mutableMapOf<String, ListingItem>()
        val m = merchant(cache = cache)
        val r = m.publish(goodDraft)
        assertTrue(r is MerchantPosts.Outcome.Published, r.toString())
        val listing = cache.values.single()
        assertEquals("Grand opening: Juice Junction", listing.title("en"))
        assertEquals(now, listing.firstListedAtEpochMs, "counts as recently launched")
        assertTrue(BookingDestinations.isBookable(listing), "its link adds Book Now")

        // Shows up in a "what's new" search on the device.
        val resolver = resolver(cache.values.toList(), here = GeoCoordinate(listing.lat, listing.lng))
        val fresh = resolver.resolve(Binding(ListingQuery(freshness = Freshness.NEW, near = GeoFilter(GeoAnchor.Here, 6_000))))
        assertEquals(listing.id, fresh.single().item.id)
    }

    @Test
    fun badPostsAreRejectedWithReasons() {
        val m = merchant()
        val r = m.publish(MerchantDraft(title = "x", description = "short", area = "Atlantis", launchDate = "2020-01-01", link = "http://bad"))
        val problems = (r as MerchantPosts.Outcome.Rejected).problems
        listOf("title", "what you're announcing", "description", "Atlantis", "past", "https").forEach { key ->
            assertTrue(problems.any { it.contains(key, ignoreCase = true) }, "expected a problem about $key in $problems")
        }
        assertTrue(m.publish(goodDraft.copy(description = "Guaranteed returns on every juice!")) is MerchantPosts.Outcome.Rejected, "moderation")
        assertTrue(m.publish(goodDraft.copy(type = OfferingType.EVENT)) is MerchantPosts.Outcome.Rejected, "events need a date")
    }

    @Test
    fun duplicatesAreRefusedAndPostsCanBeRemoved() {
        val cache = mutableMapOf<String, ListingItem>()
        val m = merchant(cache = cache)
        val post = (m.publish(goodDraft) as MerchantPosts.Outcome.Published).post
        assertTrue(m.publish(goodDraft) is MerchantPosts.Outcome.Rejected)
        m.remove(post.id)
        assertTrue(cache.isEmpty())
        assertTrue(m.posts.value.isEmpty())
    }

    // ---- 11.2 / 11.5 Suggestions and history ----

    @Test
    fun suggestionsCompleteCategoriesHistoryAndNames() {
        val s = SearchSuggestions.complete("tem", history = listOf("temples in Jayanagar"), placeNames = listOf("Temple Tree Cafe", "ISKCON Temple"))
        assertEquals("temples in Jayanagar", s.first(), "history first")
        assertTrue("temples near me" in s, s.toString())
        assertTrue("Temple Tree Cafe" in s, "names that start with what was typed")
        assertTrue("temples near me" in SearchSuggestions.complete("temle", emptyList(), emptyList()), "typos")
    }

    @Test
    fun historyRecordsRemovesClearsAndCanBeTurnedOff() {
        var t = 0L
        val h = SearchHistory(InMemoryKeyValueStore(), { t++ })
        h.record("temples near me"); h.record("salons"); h.record("Temples near me")
        assertEquals(listOf("Temples near me", "salons"), h.entries.value.map { it.text }, "newest first, no duplicates")
        h.remove("salons")
        assertEquals(1, h.entries.value.size)
        h.enabled = false
        assertTrue(h.entries.value.isEmpty())
        h.record("anything")
        assertTrue(h.entries.value.isEmpty())
    }

    // ---- helpers ----

    private fun item(id: String, category: String, lat: Double, lng: Double, name: String = id) = ListingItem(
        id = id, titles = mapOf("en" to name), category = category, lat = lat, lng = lng,
        regionCode = "IN-KA-BLR", timeZone = "Asia/Kolkata", firstListedAtEpochMs = 0,
    )

    private fun resolver(items: List<ListingItem>, here: GeoCoordinate = GeoCoordinate(12.9716, 77.5946)) =
        ListingResolver(ListingSource { items }, Gazetteer.Bangalore, LocationProvider { here }, LocalRanker(InMemorySignalLog()), { now })

    private fun pipeline(): IntentPipeline {
        val signals = InMemorySignalLog()
        var n = 0
        return IntentPipeline(
            TieredAiEngine(RuleBasedIntentParser(Gazetteer.Bangalore), emptyList()), SurfaceComposer { "s${++n}" },
            DslValidator({ true }), InMemoryProfileStore(), signals, LocalRanker(signals), { 0L },
        )
    }
}
