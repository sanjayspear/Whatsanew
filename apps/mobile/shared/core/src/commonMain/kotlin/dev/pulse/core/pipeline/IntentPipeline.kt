package dev.pulse.core.pipeline

import dev.pulse.core.ai.AiEngine
import dev.pulse.core.ai.Clarification
import dev.pulse.core.ai.Clarifier
import dev.pulse.core.data.SearchDefaults
import dev.pulse.core.ai.IntentContext
import dev.pulse.core.ai.IntentInput
import dev.pulse.core.prefs.ProfileStore
import dev.pulse.core.signals.LocalRanker
import dev.pulse.core.signals.Signal
import dev.pulse.core.signals.SignalLog
import dev.pulse.dsl.ActionSpec
import dev.pulse.dsl.DslValidator
import dev.pulse.dsl.FrameSource
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.Node
import dev.pulse.dsl.Roles
import dev.pulse.dsl.Task
import dev.pulse.dsl.UiPatch
import dev.pulse.dsl.IntentFrame
import dev.pulse.dsl.Persistence
import dev.pulse.dsl.ReduceResult
import dev.pulse.dsl.Surface
import dev.pulse.dsl.SurfaceComposer
import dev.pulse.dsl.SurfaceReducer
import dev.pulse.dsl.ThemeSpec
import dev.pulse.dsl.UiOp
import dev.pulse.dsl.ValidationIssue
import dev.pulse.dsl.hasErrors
import kotlin.time.TimeSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the UI needs to paint. Immutable; one emission per repaint. */
data class SurfaceState(
    val surface: Surface? = null,
    val theme: ThemeSpec = ThemeSpec(),
    val lastUtterance: String? = null,
    val frameSource: FrameSource? = null,
    /** Utterance -> applied patch, measured on-device. Budget: < 300 ms to first paint. */
    val lastLatencyMs: Long? = null,
    val issues: List<ValidationIssue> = emptyList(),
)

/**
 * input -> AiEngine (frames) -> SurfaceComposer (patch) -> DslValidator -> SurfaceReducer -> state.
 *
 * Every frame source (rules, on-device LLM, server) goes through the same validator and atomic
 * reducer. A bad model output is dropped and the previous screen stays intact.
 */
class IntentPipeline(
    private val engine: AiEngine,
    private val composer: SurfaceComposer,
    private val validator: DslValidator,
    private val profile: ProfileStore,
    private val signals: SignalLog,
    private val ranker: LocalRanker,
    private val nowMs: () -> Long,
) {
    private val _state = MutableStateFlow(SurfaceState(theme = profile.theme()))
    val state: StateFlow<SurfaceState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<ActionSpec>(extraBufferCapacity = 8)
    /** ACT intents ("call the first one") for the host to resolve against what's on screen. */
    val actions: SharedFlow<ActionSpec> = _actions.asSharedFlow()

    suspend fun submit(input: IntentInput) {
        val mark = TimeSource.Monotonic.markNow()
        // Ambiguous request: ask instead of guessing (the answer comes back as a normal, clearer request).
        if (input.visionTags.isEmpty()) Clarifier.question(input.text)?.let { q ->
            showClarification(q, input.text, mark.elapsedNow().inWholeMilliseconds)
            return
        }
        val current = _state.value.surface
        val context = IntentContext(
            visibleRoles = current?.root?.children?.mapNotNull { it.role }.orEmpty(),
            hasSurface = current != null,
        )
        engine.understand(input, context).collect { event ->
            apply(event.frame, input.text, mark.elapsedNow().inWholeMilliseconds)
        }
    }

    /** Applies one frame. Public for tests and for server-pushed frames. */
    fun apply(input: IntentFrame, utterance: String, latencyMs: Long) {
        val before = _state.value
        // Searches that don't say where mean "around me, within my default radius" (Settings, 6 km by default).
        val q = input.query
        val frame = if (input.task == Task.DISCOVER && q != null && q.near == null) {
            input.copy(query = q.copy(near = GeoFilter(GeoAnchor.Here, SearchDefaults.radiusMeters)))
        } else input
        val result = composer.compose(frame, before.surface, profile.layout(), profile.theme())
        result.action?.let { _actions.tryEmit(it) }

        val issues = validator.validate(result.patch)
        if (issues.hasErrors()) {
            _state.value = before.copy(issues = issues)
            return
        }

        if (result.patch.persistence == Persistence.PROFILE) {
            profile.saveLayout(result.prefs)
            result.patch.ops.filterIsInstance<UiOp.UpdateTheme>().forEach { profile.saveTheme(profile.theme().apply(it.patch)) }
        }

        val surface = when {
            result.patch.ops.isEmpty() -> before.surface
            else -> when (val r = SurfaceReducer.reduce(before.surface, result.patch)) {
                is ReduceResult.Applied -> r.surface
                // Theme-only patch on the landing screen: profile updated above, no surface yet.
                is ReduceResult.Rejected -> before.surface
            }
        }
        frame.query?.categories?.forEach { signals.append(Signal(Signal.Kind.REFINE, it, atEpochMs = nowMs())) }
        _state.value = SurfaceState(
            surface = surface,
            theme = surface?.theme ?: profile.theme(),
            lastUtterance = utterance,
            frameSource = frame.source,
            lastLatencyMs = latencyMs,
            issues = issues,
        )
    }

    private fun showClarification(q: Clarification, utterance: String, latencyMs: Long) {
        val options = JsonArray(q.options.map { o -> JsonObject(mapOf("label" to JsonPrimitive(o.label), "utterance" to JsonPrimitive(o.utterance))) })
        val node = Node(
            "clarify", CLARIFY_COMPONENT, role = Roles.PICKER,
            props = JsonObject(mapOf("question" to JsonPrimitive(q.question), "options" to options)),
        )
        val surface = Surface("clarify-${nowMs()}", Node("root", "layout.stack", role = Roles.ROOT, children = listOf(node)), theme = profile.theme())
        val patch = UiPatch(listOf(UiOp.ReplaceSurface(surface)))
        val issues = validator.validate(patch)
        if (issues.hasErrors()) return
        _state.value = SurfaceState(surface, profile.theme(), utterance, FrameSource.RULES, latencyMs, issues)
    }

    fun record(signal: Signal) {
        signals.append(signal)
        ranker.invalidate()
    }

    fun reset() {
        _state.value = SurfaceState(theme = profile.theme())
    }

    companion object {
        /** The question card shown for ambiguous requests (registered by the UI). */
        const val CLARIFY_COMPONENT = "intent.clarify"
    }
}
