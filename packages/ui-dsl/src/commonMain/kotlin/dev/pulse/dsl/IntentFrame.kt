package dev.pulse.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the on-device model emits: a compact, typed *intent frame*, not a full UI tree.
 *
 * Why not have the LLM write the whole [UiPatch]? On the perf floor (Snapdragon 7-series), a
 * 2B INT4 model decodes about 15-25 tokens/s. A full surface is 400+ tokens (20+ s), but a
 * frame is 30-60 tokens. The deterministic [SurfaceComposer] then expands the frame into a
 * surface in under 1 ms, using the user's layout preferences. The AI decides *what* the user
 * wants, and the composer decides *how it is painted*, consistently and testably.
 *
 * [extraOps] is a validated escape hatch that lets the model insert components the composer
 * doesn't know about yet.
 */
@Serializable
data class IntentFrame(
    val task: Task,
    val query: ListingQuery? = null,
    val theme: ThemePatch? = null,
    val layout: List<LayoutEdit> = emptyList(),
    val action: ActionSpec? = null,
    val extraOps: List<UiOp> = emptyList(),
    val confidence: Float = 1f,
    val source: FrameSource = FrameSource.RULES,
)

@Serializable
enum class Task {
    /** New content search: paints a fresh surface. */
    DISCOVER,
    /** Narrows the current surface ("only ones open now"). */
    REFINE,
    CUSTOMIZE_THEME,
    CUSTOMIZE_LAYOUT,
    /** Do something with what's on screen ("call the first one"). */
    ACT,
    UNKNOWN,
}

@Serializable
enum class FrameSource { RULES, ON_DEVICE_LLM, SERVER }

/** Layout edits address nodes by semantic role (see [Roles]), never by node id. */
@Serializable
sealed interface LayoutEdit {
    val role: String

    @Serializable
    @SerialName("move")
    data class Move(override val role: String, val position: Int) : LayoutEdit

    @Serializable
    @SerialName("hide")
    data class Hide(override val role: String) : LayoutEdit

    @Serializable
    @SerialName("show")
    data class Show(override val role: String) : LayoutEdit
}

/** Well-known roles the composer emits. Components may introduce more. */
object Roles {
    const val ROOT = "root"
    const val HEADER = "header"
    const val MAP = "map"
    const val RESULTS = "results"
    const val VIDEOS = "videos"
    const val SUGGESTIONS = "suggestions"
    const val PICKER = "picker"
}
