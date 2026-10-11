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
import kotlinx.coroutines.flow.fold
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
    private val llmTimeoutMs: Long = 2_500,
) : AiEngine {

    private val _status = MutableStateFlow(EngineStatus(ladder = ladder.map { BackendState(it.id, BackendAvailability.UNAVAILABLE) }))
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val warmUpLock = Mutex()
    private var active: LanguageModelBackend? = null

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
        val raw = try {
            withTimeoutOrNull(llmTimeoutMs) {
                backend.generate(LlmPrompt.request(input, context)).fold("") { acc, chunk -> acc + chunk }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } ?: return@flow

        val refined = LlmPrompt.parse(raw) ?: return@flow
        if (refined.task == Task.UNKNOWN && provisional.task != Task.UNKNOWN) return@flow
        emit(FrameEvent.Refined(refined.copy(source = FrameSource.ON_DEVICE_LLM), backend.id, mark.elapsedNow().inWholeMilliseconds))
    }

    override fun close() {
        ladder.forEach { runCatching { it.close() } }
    }
}

/** Platform thermal state (Android PowerManager.thermalStatus, iOS ProcessInfo.thermalState). */
fun interface ThermalMonitor {
    fun isThrottled(): Boolean
}
