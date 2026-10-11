package dev.pulse.core

import dev.pulse.core.ai.IntentContext
import dev.pulse.core.ai.IntentInput
import dev.pulse.core.ai.Modality
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.data.Gazetteer
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.Freshness
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.LayoutEdit
import dev.pulse.dsl.Mood
import dev.pulse.dsl.Task
import dev.pulse.dsl.TimePreset
import dev.pulse.dsl.isAmbiguous
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleBasedIntentParserTest {

    private val parser = RuleBasedIntentParser(Gazetteer.Bangalore)
    private val onSurface = IntentContext(visibleRoles = listOf("header", "map", "results"), hasSurface = true)

    private fun parse(text: String, context: IntentContext = IntentContext(), tags: List<String> = emptyList()) =
        parser.parse(IntentInput(text, visionTags = tags), context)

    @Test
    fun newCafesInKoramangalaWithin3km() {
        val f = parse("Show me new cafes in Koramangala within 3 km")
        assertEquals(Task.DISCOVER, f.task)
        assertEquals(listOf("food.cafe"), f.query!!.categories)
        assertEquals(GeoAnchor.Place("Koramangala"), f.query!!.near!!.anchor)
        assertEquals(3_000, f.query!!.near!!.radiusMeters)
        assertEquals(Freshness.NEW, f.query!!.freshness)
        assertTrue(f.confidence >= 0.9f, "high confidence means no LLM call is needed")
    }

    @Test
    fun dramaOrTheatreNearRavindraKalakshetraThisWeekend() {
        val f = parse("Any drama or theatre near Ravindra Kalakshetra this weekend?")
        assertEquals(listOf("arts.theatre"), f.query!!.categories)
        assertEquals(GeoAnchor.Place("Ravindra Kalakshetra"), f.query!!.near!!.anchor)
        assertEquals(TimePreset.THIS_WEEKEND, f.query!!.time!!.preset)
    }

    @Test
    fun warmAndQuieterTheme() {
        val f = parse("Change my app theme to something warm and quieter.")
        assertEquals(Task.CUSTOMIZE_THEME, f.task)
        assertEquals(Mood.WARM, f.theme!!.mood)
        assertTrue(f.theme!!.motionScale!! < 1f)
        assertTrue(f.theme!!.saturation!! < 1f)
    }

    @Test
    fun moveTheMapToTheTop() {
        val f = parse("Move the map to the top of the screen.", onSurface)
        assertEquals(Task.CUSTOMIZE_LAYOUT, f.task)
        assertEquals(listOf(LayoutEdit.Move("map", 0)), f.layout)
    }

    @Test
    fun cameraTagsPlusVoiceDosaNearMe() {
        val f = parser.parse(
            IntentInput("Where can I get this near me, something cheap?", setOf(Modality.CAMERA, Modality.VOICE), listOf("dosa", "plate")),
            IntentContext(),
        )
        assertEquals(listOf("food.restaurant.south_indian"), f.query!!.categories)
        assertEquals("dosa", f.query!!.text)
        assertEquals(GeoAnchor.Here, f.query!!.near!!.anchor)
        assertEquals(1, f.query!!.priceLevelMax)
    }

    @Test
    fun showMeCafesOnAMapIsASearchNotALayoutEdit() {
        assertEquals(Task.DISCOVER, parse("show me cafes on a map", onSurface).task)
    }

    @Test
    fun hideTheVideos() {
        assertEquals(listOf(LayoutEdit.Hide("videos")), parse("hide the videos", onSurface).layout)
    }

    @Test
    fun refineOnlyOpenNowNarrowsCurrentSurface() {
        val f = parse("only ones open now", onSurface)
        assertEquals(Task.REFINE, f.task)
        assertEquals(true, f.query!!.openNow)
    }

    @Test
    fun callTheFirstOneIsAnAction() {
        val f = parse("call the first one", onSurface)
        assertEquals(Task.ACT, f.task)
        assertEquals(ActionKind.CALL, f.action!!.kind)
        assertEquals("results[0]", f.action!!.args["target"])
    }

    @Test
    fun radiusUnitsAreConvertedAndClamped() {
        assertEquals(800, parse("cafes within 800 m").query!!.near!!.radiusMeters)
        assertEquals(50_000, parse("gyms within 900 km").query!!.near!!.radiusMeters)
    }

    @Test
    fun freeTextResidueBecomesTextQuery() {
        val f = parse("rooftop board games")
        assertEquals(Task.DISCOVER, f.task)
        assertEquals("rooftop board games", f.query!!.text)
        assertTrue(f.confidence < 0.75f, "fuzzy queries should escalate to the LLM")
    }

    @Test
    fun gibberishIsUnknown() {
        assertEquals(Task.UNKNOWN, parse("??").task)
    }

    @Test
    fun fillerWordsAreUnknownNotTextSearches() {
        assertEquals(Task.UNKNOWN, parse("hmm").task)
        assertEquals(Task.UNKNOWN, parse("okay thanks").task)
    }

    @Test
    fun vagueNearbyRequestIsAmbiguousSoThePickerShows() {
        val f = parse("show me something nearby")
        assertEquals(Task.DISCOVER, f.task)
        assertEquals(null, f.query!!.text, "'nearby' is a location word, not a search term")
        assertTrue(f.query!!.isAmbiguous())
    }
}
