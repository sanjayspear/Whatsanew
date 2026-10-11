package dev.pulse.ui.registry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ResolvedItem
import dev.pulse.core.signals.Signal
import dev.pulse.dsl.ActionSpec
import dev.pulse.dsl.Binding
import dev.pulse.dsl.ComponentCatalog
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.Node

/**
 * A renderable DSL component. To add a new one:
 *   1. implement this interface with a unique [key] (and optionally a @Serializable props class),
 *   2. add it to the registry ([ComponentRegistry.plus] or the default list).
 * The renderer, the DSL and every other component stay untouched (open/closed principle).
 */
interface PulseComponent {
    val key: String

    @Composable
    fun Render(node: Node, scope: RenderScope, modifier: Modifier)
}

class ComponentRegistry(components: Iterable<PulseComponent>) : ComponentCatalog {
    private val byKey: Map<String, PulseComponent> = components.associateBy { it.key }

    init {
        require(byKey.size == components.count()) { "duplicate component keys" }
    }

    override fun supports(component: String): Boolean = component in byKey

    operator fun get(key: String): PulseComponent? = byKey[key]

    operator fun plus(component: PulseComponent) = ComponentRegistry(byKey.values + component)

    val keys: Set<String> get() = byKey.keys
}

/**
 * Everything a component may need from the host, passed down explicitly (no globals).
 * [item] is set when the node is a binding template being rendered for one result.
 */
@Stable
class RenderScope(
    val registry: ComponentRegistry,
    val resolve: (Binding) -> List<ResolvedItem>,
    val dispatch: (ActionSpec, ResolvedItem?) -> Unit,
    val signal: (Signal) -> Unit,
    val submitIntent: (String) -> Unit,
    val languageTag: String,
    val nowMs: () -> Long,
    val anchorOf: (GeoAnchor?) -> GeoCoordinate,
    val item: ResolvedItem? = null,
) {
    fun withItem(item: ResolvedItem) = RenderScope(registry, resolve, dispatch, signal, submitIntent, languageTag, nowMs, anchorOf, item)

    /** Renders any node through the registry; unknown components render nothing (forward-compatible). */
    @Composable
    fun RenderNode(node: Node, modifier: Modifier = Modifier) {
        registry[node.component]?.Render(node, this, modifier)
    }
}
