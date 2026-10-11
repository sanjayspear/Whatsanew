package dev.pulse.core

import dev.pulse.core.ai.IntentInput
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.ai.TieredAiEngine
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.pipeline.IntentPipeline
import dev.pulse.core.prefs.InMemoryProfileStore
import dev.pulse.core.signals.InMemorySignalLog
import dev.pulse.core.signals.LocalRanker
import dev.pulse.dsl.DslValidator
import dev.pulse.dsl.Mood
import dev.pulse.dsl.Roles
import dev.pulse.dsl.SurfaceComposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Utterance in, painted surface state out: the whole on-device loop, no mocks. */
class IntentPipelineTest {

    private val profile = InMemoryProfileStore()
    private val signals = InMemorySignalLog()
    private var n = 0
    private fun pipeline() = IntentPipeline(
        engine = TieredAiEngine(RuleBasedIntentParser(Gazetteer.Bangalore), emptyList()),
        composer = SurfaceComposer { "s${++n}" },
        validator = DslValidator({ true }),
        profile = profile,
        signals = signals,
        ranker = LocalRanker(signals),
        nowMs = { 0L },
    )

    @Test
    fun conversationOfFourIntentsProducesExpectedScreens() = runTest {
        val p = pipeline()

        p.submit(IntentInput("Show me new cafes in Koramangala within 3 km"))
        assertEquals(listOf(Roles.HEADER, Roles.MAP, Roles.RESULTS), p.state.value.surface!!.root.children.map { it.role })

        p.submit(IntentInput("Change my app theme to something warm and quieter."))
        assertEquals(Mood.WARM, p.state.value.theme.mood)

        p.submit(IntentInput("Move the map to the top of the screen."))
        assertEquals(Roles.MAP, p.state.value.surface!!.root.children.first().role)

        // Preferences persist into the next, brand-new surface.
        p.submit(IntentInput("Any drama or theatre near Ravindra Kalakshetra this weekend?"))
        val s = p.state.value
        assertEquals(Roles.MAP, s.surface!!.root.children.first().role)
        assertEquals(Mood.WARM, s.theme.mood)
        assertNotNull(s.surface!!.root.children.firstOrNull { it.role == Roles.VIDEOS })
        assertTrue(s.lastLatencyMs!! < 300, "intent -> paint budget")
    }

    @Test
    fun themeOnLandingScreenIsSavedToProfile() = runTest {
        val p = pipeline()
        p.submit(IntentInput("make the theme dark"))
        assertEquals(Mood.NOIR, profile.theme().mood)
        assertEquals(Mood.NOIR, p.state.value.theme.mood)
    }
}
