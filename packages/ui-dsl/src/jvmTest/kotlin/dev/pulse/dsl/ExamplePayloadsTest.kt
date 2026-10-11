package dev.pulse.dsl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

/**
 * Golden tests over the JSON files in `examples/`, the same files used as few-shot examples for the
 * on-device model and as fixtures by the AI server's eval harness. If the schema drifts,
 * this fails before any model is trained on stale examples.
 */
class ExamplePayloadsTest {

    @Serializable
    data class Example(
        val utterance: String,
        val modalities: List<String>,
        val visionTags: List<String> = emptyList(),
        val frame: IntentFrame,
    )

    private val examplesDir = File(System.getProperty("pulse.examplesDir") ?: "examples")
    private val examples: Map<String, Example> = examplesDir.listFiles { f -> f.extension == "json" }!!
        .sortedBy { it.name }
        .associate { it.nameWithoutExtension to PulseJson.decodeFromString(Example.serializer(), it.readText()) }

    private var seq = 0
    private val composer = SurfaceComposer { "surface-${++seq}" }
    private val validator = DslValidator({ true })

    private fun run(name: String, current: Surface? = null, prefs: LayoutPrefs = LayoutPrefs()): Pair<Surface, ComposeResult> {
        val result = composer.compose(examples.getValue(name).frame, current, prefs)
        assertFalse(validator.validate(result.patch).hasErrors(), "$name: ${validator.validate(result.patch)}")
        val surface = when (val r = SurfaceReducer.reduce(current, result.patch)) {
            is ReduceResult.Applied -> r.surface
            is ReduceResult.Rejected -> current ?: error("$name rejected: ${r.reason}")
        }
        return surface to result
    }

    @Test
    fun `all five examples decode`() {
        assertEquals(5, examples.size)
    }

    @Test
    fun `01 new cafes in Koramangala within 3 km`() {
        val (s, _) = run("01-new-cafes-koramangala")
        val q = s.root.findByRole(Roles.RESULTS)!!.bind!!.query
        assertEquals(listOf("food.cafe"), q.categories)
        assertEquals(GeoAnchor.Place("Koramangala"), q.near!!.anchor)
        assertEquals(3000, q.near!!.radiusMeters)
        assertEquals(Freshness.NEW, q.freshness)
    }

    @Test
    fun `02 theatre near Ravindra Kalakshetra this weekend`() {
        val (s, _) = run("02-theatre-ravindra-kalakshetra")
        assertTrue(s.root.findByRole(Roles.VIDEOS) != null, "event surfaces include trailers")
        assertEquals(TimePreset.THIS_WEEKEND, s.root.findByRole(Roles.RESULTS)!!.bind!!.query.time!!.preset)
    }

    @Test
    fun `03 warm and quieter theme`() {
        val (base, _) = run("01-new-cafes-koramangala")
        val (s, result) = run("03-warm-quiet-theme", current = base)
        assertEquals(Persistence.PROFILE, result.patch.persistence)
        assertEquals(Mood.WARM, s.theme.mood)
        assertTrue(s.theme.motionScale < 1f)
    }

    @Test
    fun `04 move the map to the top`() {
        val (base, _) = run("01-new-cafes-koramangala")
        val (s, result) = run("04-map-to-top", current = base)
        assertEquals(Roles.MAP, s.root.children.first().role)
        assertEquals(Roles.MAP, result.prefs.order.first())
    }

    @Test
    fun `05 camera plus voice - dosa near me`() {
        val (s, _) = run("05-photo-dosa-near-me")
        val q = s.root.findByRole(Roles.RESULTS)!!.bind!!.query
        assertEquals("dosa", q.text)
        assertEquals(GeoAnchor.Here, q.near!!.anchor)
        assertEquals(true, q.openNow)
    }

    @Test
    fun `frames round-trip through JSON`() {
        examples.values.forEach { ex ->
            val json = PulseJson.encodeToString(IntentFrame.serializer(), ex.frame)
            assertEquals(ex.frame, PulseJson.decodeFromString(IntentFrame.serializer(), json))
        }
    }

    @Test
    fun `composed surfaces round-trip through JSON`() {
        val (s, _) = run("02-theatre-ravindra-kalakshetra")
        val json = PulseJson.encodeToString(Surface.serializer(), s)
        assertIs<Surface>(PulseJson.decodeFromString(Surface.serializer(), json))
        assertEquals(s, PulseJson.decodeFromString(Surface.serializer(), json))
    }
}
