package dev.pulse.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * An ordered, atomic list of mutations to the current [Surface]. Either every op applies or
 * none does (see [SurfaceReducer]).
 */
@Serializable
data class UiPatch(
    val ops: List<UiOp>,
    val persistence: Persistence = Persistence.SESSION,
    val schemaVersion: Int = DSL_VERSION,
)

@Serializable
enum class Persistence {
    /** Applies to the current surface only. */
    SESSION,
    /** Also saved as a user preference and re-applied to future surfaces (theme, layout). */
    PROFILE,
}

@Serializable
sealed interface UiOp {
    @Serializable
    @SerialName("surface.replace")
    data class ReplaceSurface(val surface: Surface) : UiOp

    /** [index] -1 appends. */
    @Serializable
    @SerialName("node.insert")
    data class InsertNode(val parentId: String, val node: Node, val index: Int = -1) : UiOp

    @Serializable
    @SerialName("node.remove")
    data class RemoveNode(val nodeId: String) : UiOp

    @Serializable
    @SerialName("node.move")
    data class MoveNode(val nodeId: String, val parentId: String, val index: Int) : UiOp

    /** Shallow-merges [props] into the node's props; replaces the binding if given. */
    @Serializable
    @SerialName("node.update")
    data class UpdateNode(
        val nodeId: String,
        val props: JsonObject? = null,
        val bind: Binding? = null,
    ) : UiOp

    @Serializable
    @SerialName("theme.update")
    data class UpdateTheme(val patch: ThemePatch) : UiOp
}
