package dev.pulse.dsl

import kotlinx.serialization.json.JsonObject

sealed interface ReduceResult {
    data class Applied(val surface: Surface) : ReduceResult
    data class Rejected(val reason: String, val opIndex: Int) : ReduceResult
}

/**
 * Pure `(Surface?, UiPatch) -> Surface` state transition. Patches are atomic: the first
 * invalid op rejects the whole patch and the caller keeps its previous surface. A bad model
 * output therefore can never leave the screen half-mutated.
 */
object SurfaceReducer {

    fun reduce(current: Surface?, patch: UiPatch): ReduceResult {
        if (patch.schemaVersion > DSL_VERSION) {
            return ReduceResult.Rejected("schemaVersion ${patch.schemaVersion} > supported $DSL_VERSION", -1)
        }
        var surface = current
        patch.ops.forEachIndexed { index, op ->
            surface = try {
                apply(surface, op)
            } catch (e: OpFailure) {
                return ReduceResult.Rejected(e.message ?: "invalid op", index)
            }
        }
        return surface?.let { ReduceResult.Applied(it) }
            ?: ReduceResult.Rejected("patch produced no surface", -1)
    }

    private fun apply(surface: Surface?, op: UiOp): Surface = when (op) {
        is UiOp.ReplaceSurface -> op.surface
        is UiOp.InsertNode -> requireSurface(surface, op).let { s ->
            check(s.root.findById(op.node.id) == null) { "node '${op.node.id}' already exists" }
            requireNode(s, op.parentId)
            s.copy(root = s.root.mapNode(op.parentId) { it.copy(children = it.children.insertAt(op.index, op.node)) })
        }
        is UiOp.RemoveNode -> requireSurface(surface, op).let { s ->
            check(op.nodeId != s.root.id) { "cannot remove root" }
            requireNode(s, op.nodeId)
            s.copy(root = s.root.removeNode(op.nodeId))
        }
        is UiOp.MoveNode -> requireSurface(surface, op).let { s ->
            check(op.nodeId != s.root.id) { "cannot move root" }
            val node = requireNode(s, op.nodeId)
            requireNode(s, op.parentId)
            check(node.findById(op.parentId) == null) { "cannot move '${op.nodeId}' into its own subtree" }
            val detached = s.root.removeNode(op.nodeId)
            s.copy(root = detached.mapNode(op.parentId) { it.copy(children = it.children.insertAt(op.index, node)) })
        }
        is UiOp.UpdateNode -> requireSurface(surface, op).let { s ->
            requireNode(s, op.nodeId)
            s.copy(root = s.root.mapNode(op.nodeId) { node ->
                node.copy(
                    props = op.props?.let { JsonObject(node.props + it) } ?: node.props,
                    bind = op.bind ?: node.bind,
                )
            })
        }
        is UiOp.UpdateTheme -> requireSurface(surface, op).let { s -> s.copy(theme = s.theme.apply(op.patch)) }
    }

    private fun requireSurface(surface: Surface?, op: UiOp): Surface =
        surface ?: throw OpFailure("${op::class.simpleName} needs an existing surface")

    private fun requireNode(surface: Surface, id: String): Node =
        surface.root.findById(id) ?: throw OpFailure("node '$id' not found")

    private fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw OpFailure(message())
    }

    private fun List<Node>.insertAt(index: Int, node: Node): List<Node> {
        val at = if (index < 0 || index > size) size else index
        return toMutableList().apply { add(at, node) }
    }

    private class OpFailure(message: String) : Exception(message)
}
