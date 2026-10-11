package dev.pulse.core.ai

import dev.pulse.dsl.IntentFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single on-device AI entry point used by the intent pipeline.
 *
 * Contract: [understand] emits a *provisional* frame from the deterministic fast path within a
 * few milliseconds (so the UI paints well under the 300 ms budget), then optionally a *refined*
 * frame from the on-device LLM. The pipeline repaints on each emission. The flow never errors:
 * backend failures fall down the ladder, and the final rung (rules) always answers.
 */
interface AiEngine {
    val status: StateFlow<EngineStatus>

    /** Loads model weights / opens sessions off the main thread. Safe to call repeatedly. */
    suspend fun warmUp()

    fun understand(input: IntentInput, context: IntentContext): Flow<FrameEvent>

    fun close()
}

/** Multimodal input. Voice arrives here already transcribed by [SpeechEngine]. */
data class IntentInput(
    val text: String,
    val modality: Set<Modality> = setOf(Modality.TEXT),
    /** Labels from on-device vision (MediaPipe object detection / OCR) for camera or video input. */
    val visionTags: List<String> = emptyList(),
)

enum class Modality { TEXT, VOICE, CAMERA, VIDEO }

/** What the model needs to know about the screen to resolve "move *the map*" or "only cheaper ones". */
data class IntentContext(
    val visibleRoles: List<String> = emptyList(),
    val hasSurface: Boolean = false,
    val languageTag: String = "en-IN",
)

sealed interface FrameEvent {
    val frame: IntentFrame
    data class Provisional(override val frame: IntentFrame) : FrameEvent
    data class Refined(override val frame: IntentFrame, val backendId: String, val latencyMs: Long) : FrameEvent
}

data class EngineStatus(
    val activeBackend: String = "rules",
    val ladder: List<BackendState> = emptyList(),
    val thermalThrottled: Boolean = false,
)

data class BackendState(val id: String, val availability: BackendAvailability)
