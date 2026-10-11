package dev.pulse.core

import dev.pulse.core.ai.BackendAvailability
import dev.pulse.core.ai.FrameEvent
import dev.pulse.core.ai.GenerationRequest
import dev.pulse.core.ai.IntentContext
import dev.pulse.core.ai.IntentInput
import dev.pulse.core.ai.LanguageModelBackend
import dev.pulse.core.ai.LlmPrompt
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.ai.TieredAiEngine
import dev.pulse.core.data.Gazetteer
import dev.pulse.dsl.FrameSource
import dev.pulse.dsl.Task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class AiLadderTest {

    private class FakeBackend(
        private val available: Boolean = true,
        private val output: () -> Flow<String>,
    ) : LanguageModelBackend {
        var calls = 0
        override val id = "fake"
        override suspend fun availability() = if (available) BackendAvailability.AVAILABLE else BackendAvailability.UNAVAILABLE
        override fun generate(request: GenerationRequest): Flow<String> { calls++; return output() }
    }

    private val rules = RuleBasedIntentParser(Gazetteer.Bangalore)
    private val fuzzy = IntentInput("somewhere quiet to read with good coffee")

    @Test
    fun confidentRulesSkipTheLlmEntirely() = runTest {
        val backend = FakeBackend { flowOf("""{"task":"UNKNOWN"}""") }
        val engine = TieredAiEngine(rules, listOf(backend)).also { it.warmUp() }
        val events = engine.understand(IntentInput("new cafes in Koramangala within 3 km"), IntentContext()).toList()
        assertIs<FrameEvent.Provisional>(events.single())
        assertEquals(0, backend.calls)
    }

    @Test
    fun ambiguousInputIsRefinedByTheLlm() = runTest {
        val backend = FakeBackend {
            flowOf("Sure! ", """{"task":"DISCOVER","query":{"categories":["food.cafe"],"text":"quiet reading"},"confidence":0.8}""", " done")
        }
        val engine = TieredAiEngine(rules, listOf(backend)).also { it.warmUp() }
        val events = engine.understand(fuzzy, IntentContext()).toList()
        assertEquals(2, events.size)
        val refined = assertIs<FrameEvent.Refined>(events[1])
        assertEquals(FrameSource.ON_DEVICE_LLM, refined.frame.source)
        assertEquals(listOf("food.cafe"), refined.frame.query!!.categories)
    }

    @Test
    fun garbageLlmOutputFallsBackToRules() = runTest {
        val engine = TieredAiEngine(rules, listOf(FakeBackend { flowOf("I think you want coffee!") })).also { it.warmUp() }
        assertIs<FrameEvent.Provisional>(engine.understand(fuzzy, IntentContext()).toList().single())
    }

    @Test
    fun crashingBackendFallsBackToRules() = runTest {
        val engine = TieredAiEngine(rules, listOf(FakeBackend { flow { error("GPU delegate lost") } })).also { it.warmUp() }
        assertIs<FrameEvent.Provisional>(engine.understand(fuzzy, IntentContext()).toList().single())
    }

    @Test
    fun hungBackendTimesOut() = runTest {
        val engine = TieredAiEngine(rules, listOf(FakeBackend { flow { awaitCancellation() } }), llmTimeoutMs = 100).also { it.warmUp() }
        assertIs<FrameEvent.Provisional>(engine.understand(fuzzy, IntentContext()).toList().single())
    }

    @Test
    fun thermalThrottlingSkipsTheLlm() = runTest {
        val backend = FakeBackend { flowOf("""{"task":"DISCOVER"}""") }
        val engine = TieredAiEngine(rules, listOf(backend), thermal = { true }).also { it.warmUp() }
        engine.understand(fuzzy, IntentContext()).toList()
        assertEquals(0, backend.calls)
        assertEquals(true, engine.status.value.thermalThrottled)
    }

    @Test
    fun unavailableBackendsAreSkippedInLadderOrder() = runTest {
        val engine = TieredAiEngine(rules, listOf(FakeBackend(available = false) { flowOf("") })).also { it.warmUp() }
        assertEquals("rules", engine.status.value.activeBackend)
    }

    @Test
    fun parserExtractsFirstBalancedObjectIgnoringBracesInStrings() {
        val frame = LlmPrompt.parse("""noise {"task":"DISCOVER","query":{"text":"a } b"}} trailing {"task":"ACT"}""")
        assertEquals(Task.DISCOVER, frame!!.task)
        assertEquals("a } b", frame.query!!.text)
        assertNull(LlmPrompt.parse("""{"task": """))
    }

    // ---- Grounding: observed on a Pixel 8a, the 0.5B model copied its few-shot example ----

    private val parrot = """{"task":"DISCOVER","query":{"categories":["food.cafe"],"near":{"anchor":{"type":"place","name":"Koramangala"},"radiusMeters":3000},"freshness":"NEW"},"confidence":0.95}"""

    @Test
    fun fullyUnderstoodRequestsNeverWakeTheModel() = runTest {
        val backend = FakeBackend { flowOf(parrot) }
        val engine = TieredAiEngine(rules, listOf(backend)).also { it.warmUp() }
        val events = engine.understand(IntentInput("what's new near me"), IntentContext()).toList()
        assertIs<FrameEvent.Provisional>(events.single())
        assertEquals(0, backend.calls)
    }

    @Test
    fun modelCannotInventAPlaceTheUserNeverSaid() = runTest {
        val engine = TieredAiEngine(rules, listOf(FakeBackend { flowOf(parrot) })).also { it.warmUp() }
        val refined = engine.understand(IntentInput("somewhere quiet to read near me"), IntentContext()).toList().last()
        val q = refined.frame.query!!
        assertEquals(dev.pulse.dsl.GeoAnchor.Here, q.near!!.anchor, "invented Koramangala must be dropped")
        assertEquals(listOf("food.cafe"), q.categories, "a missing category may be filled")
    }

    @Test
    fun modelCannotOverrideWhatRulesUnderstood() {
        val ruleFrame = rules.parse(IntentInput("fashion near Jalahalli tonight"), IntentContext())
        val merged = dev.pulse.core.ai.FrameMerge.merge(ruleFrame, LlmPrompt.parse(parrot)!!) { false }
        assertEquals(listOf("fashion"), merged.query!!.categories)
        assertEquals(dev.pulse.dsl.GeoAnchor.Place("Jalahalli"), merged.query!!.near!!.anchor)
    }

    @Test
    fun placesTheUserDidSayAreAccepted() {
        assertTrue(rules.mentionsPlace(IntentInput("quiet spot in kormangala to read"), "Koramangala"), "alias counts")
    }
}
