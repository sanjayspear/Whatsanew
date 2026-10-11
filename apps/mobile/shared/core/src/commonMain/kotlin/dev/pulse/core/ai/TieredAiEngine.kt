package dev.pulse.core.ai

import dev.pulse.dsl.FrameSource
import dev.pulse.dsl.Task
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fallback ladder: rules -> best available on-device LLM -> rules result.
 *
 * - The rules frame is always emitted first (Provisional), so first paint never waits on a model.
 * - The LLM runs only when the rules are unsure (confidence below [llmThreshold]) and the device
 *   isn't thermally throttled. This keeps battery cost proportional to ambiguity.
 * - LLM output is parsed and validated. Garbage becomes "no refinement", never a crash or a
 *   broken screen.
 */
class TieredAiEngine(
    private val rules: RuleBasedIntentParser,
    private val ladder: List<LanguageModelBackend>,
    private val thermal: ThermalMonitor = ThermalMonitor { false },
    private val llmThreshold: Float = 0.75f,
    /** Refinement happens after first paint, so it may take a few seconds without blocking anyone. */
    private val llmTimeoutMs: Long = 8_000,
) : AiEngine {

    private val _status = MutableStateFlow(EngineStatus(ladder = ladder.map { BackendState(it.id, BackendAvailability.UNAVAILABLE) }))
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val warmUpLock = Mutex()
    private var active: LanguageModelBackend? = null

    override suspend fun refresh() {
        warmUpLock.withLock {
            active?.let { runCatching { it.close() } }
            active = null
        }
        warmUp()
    }

    override suspend fun warmUp() = warmUpLock.withLock {
        if (active != null) return@withLock
        val states = ladder.map { BackendState(it.id, runCatching { it.availability() }.getOrDefault(BackendAvailability.UNAVAILABLE)) }
        active = ladder.zip(states).firstOrNull { (_, s) -> s.availability == BackendAvailability.AVAILABLE }?.first
        active?.let { backend -> runCatching { backend.prepare() }.onFailure { active = null } }
        _status.value = EngineStatus(activeBackend = active?.id ?: "rules", ladder = states)
    }

    override fun understand(input: IntentInput, context: IntentContext): Flow<FrameEvent> = flow {
        val provisional = rules.parse(input, context)
        emit(FrameEvent.Provisional(provisional))

        val backend = active ?: return@flow
        val throttled = thermal.isThrottled()
        _status.value = _status.value.copy(thermalThrottled = throttled)
        if (provisional.confidence >= llmThreshold || throttled) return@flow

        val mark = TimeSource.Monotonic.markNow()
        var failure: String? = null
        var acc = ""
        var firstTokenMs = -1L
        val raw = try {
            withTimeoutOrNull(llmTimeoutMs) {
                // Stop as soon as the first complete JSON object has arrived; upstream is cancelled.
                backend.generate(LlmPrompt.request(input, context))
                    .takeWhile { chunk ->
                        if (firstTokenMs < 0) firstTokenMs = mark.elapsedNow().inWholeMilliseconds
                        acc += chunk
                        LlmPrompt.parse(acc) == null
                    }
                    .collect()
                acc
            } ?: run {
                failure = "timeout ${llmTimeoutMs} ms · first token ${firstTokenMs} ms · ${acc.length} chars: " +
                    acc.take(60).replace('\n', ' ')
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failure = "error: ${e.message?.take(60)}"
            null
        }
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        if (raw == null) {
            record(failure ?: "no output")
            return@flow
        }
        val proposed = LlmPrompt.parse(raw)
        if (proposed == null) {
            record("unparseable after $elapsed ms: ${raw.take(80).replace('\n', ' ')}")
            return@flow
        }
        if (proposed.task == Task.UNKNOWN && provisional.task != Task.UNKNOWN) {
            record("model unsure after $elapsed ms")
            return@flow
        }
        val merged = FrameMerge.merge(provisional, proposed) { place -> rules.mentionsPlace(input, place) }
        if (merged.query == provisional.query && merged.task == provisional.task) {
            record("no new information after $elapsed ms")
            return@flow
        }
        record("refined in $elapsed ms")
        emit(FrameEvent.Refined(merged.copy(source = FrameSource.ON_DEVICE_LLM), backend.id, elapsed))
    }

    private fun record(outcome: String) {
        _status.value = _status.value.copy(lastLlm = outcome)
    }

    override fun close() {
        ladder.forEach { runCatching { it.close() } }
    }
}

/** Platform thermal state (Android PowerManager.thermalStatus, iOS ProcessInfo.thermalState). */
fun interface ThermalMonitor {
    fun isThrottled(): Boolean
}
