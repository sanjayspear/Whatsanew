package dev.pulse.dsl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Bump on breaking changes. Old clients ignore unknown fields and skip unknown components. */
const val DSL_VERSION: Int = 1

/** One full screen. Every non-landing screen in Pulse is a [Surface]; none are hardcoded. */
@Serializable
data class Surface(
    val id: String,
    val root: Node,
    val theme: ThemeSpec = ThemeSpec(),
)

/**
 * A node in the surface tree.
 *
 * [component] is an open-ended registry key (`glass.card`, `map.stack`, ...), and [props] is
 * component-owned JSON that only that component decodes. That is what makes the registry
 * extensible without touching the DSL or the renderer: shipping a new component means
 * registering a new key, nothing else.
 */
@Serializable
data class Node(
    val id: String,
    val component: String,
    /** Semantic handle for layout edits: users say "the map", not "node m-12". */
    val role: String? = null,
    val props: JsonObject = EmptyProps,
    val bind: Binding? = null,
    val actions: List<ActionSpec> = emptyList(),
    val children: List<Node> = emptyList(),
)

val EmptyProps: JsonObject = JsonObject(emptyMap())

/** A data-bound container: resolve [query], rank, and render each item with [template]. */
@Serializable
data class Binding(
    val query: ListingQuery,
    val rank: RankSpec = RankSpec(),
    val limit: Int = 20,
    val template: Node? = null,
) {
    companion object {
        const val MAX_LIMIT = 50
    }
}

/**
 * Something a card can *do*. Arg values may reference the bound item with `$item.<field>`,
 * e.g. `{"phone": "$item.phone"}`, so templates never embed real-world data.
 */
@Serializable
data class ActionSpec(
    val kind: ActionKind,
    val args: Map<String, String> = emptyMap(),
)

@Serializable
enum class ActionKind { CALL, NAVIGATE, OPEN_URL, PLAY_VIDEO, BOOK, SAVE, SHARE, REFINE }

/** Implemented by the client's component registry; keeps this package UI-toolkit-free. */
fun interface ComponentCatalog {
    fun supports(component: String): Boolean
}

// ---- Tree helpers (pure, immutable) ----

fun Node.walk(): Sequence<Node> = sequence {
    yield(this@walk)
    children.forEach { yieldAll(it.walk()) }
    bind?.template?.let { yieldAll(it.walk()) }
}

fun Node.findById(id: String): Node? = walk().firstOrNull { it.id == id }

fun Node.findByRole(role: String): Node? = walk().firstOrNull { it.role == role }

fun Node.parentOf(id: String): Node? =
    walk().firstOrNull { candidate -> candidate.children.any { it.id == id } }

fun Node.depth(): Int = 1 + (children.maxOfOrNull { it.depth() } ?: 0)

/** Returns a copy with [transform] applied to the node with [id] (and only that node). */
fun Node.mapNode(id: String, transform: (Node) -> Node): Node =
    if (this.id == id) transform(this)
    else copy(children = children.map { it.mapNode(id, transform) })

fun Node.removeNode(id: String): Node =
    copy(children = children.filterNot { it.id == id }.map { it.removeNode(id) })
