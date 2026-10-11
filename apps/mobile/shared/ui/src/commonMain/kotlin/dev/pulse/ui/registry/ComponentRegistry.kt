package dev.pulse.ui.registry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ListingItem
import dev.pulse.core.data.Resolution
import dev.pulse.core.data.ResolvedItem
import dev.pulse.core.signals.Signal
import dev.pulse.dsl.ActionSpec
import dev.pulse.dsl.Binding
import dev.pulse.dsl.ComponentCatalog
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.ListingQuery
import dev.pulse.core.prefs.UserRole
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
    val resolve: (Binding) -> Resolution,
    val dispatch: (ActionSpec, ResolvedItem?) -> Unit,
    val signal: (Signal) -> Unit,
    val submitIntent: (String) -> Unit,
    val languageTag: String,
    val nowMs: () -> Long,
    val anchorOf: (GeoAnchor?) -> GeoCoordinate,
    /**
     * Changes whenever data inputs change (e.g. the device moved > 250 m). Data-bound components
     * key their resolution on `(binding, dataVersion)`, so movement triggers a fresh fetch instead of
     * returning results cached for the old location.
     */
    val dataVersion: Any? = null,
    /** App-level actions for onboarding / settings / picker components. */
    val session: SessionActions = SessionActions.None,
    /** Live-data status, so empty lists can say "looking…" or explain an offline fallback. */
    val dataStatus: DataStatus = DataStatus(),
    val item: ResolvedItem? = null,
) {
    fun withItem(item: ResolvedItem) =
        RenderScope(registry, resolve, dispatch, signal, submitIntent, languageTag, nowMs, anchorOf, dataVersion, session, dataStatus, item)

    /** Renders any node through the registry; unknown components render nothing (forward-compatible). */
    @Composable
    fun RenderNode(node: Node, modifier: Modifier = Modifier) {
        registry[node.component]?.Render(node, this, modifier)
    }
}

/** Host actions that structural components may trigger. No-ops by default (previews, tests). */
interface SessionActions {
    val role: UserRole? get() = null

    /** Paint a new surface for a structured query (e.g. picker choice), keeping location intact. */
    fun discover(query: ListingQuery, label: String) {}

    fun setRole(role: UserRole) {}

    /** Whether a listing is on the user's shelf (drives the bookmark's on/off state). */
    fun isSaved(listingId: String): Boolean = false

    /** The user's saved places, newest first. */
    fun saved(): List<ListingItem> = emptyList()

    object None : SessionActions
}

data class DataStatus(val loading: Boolean = false, val notice: String? = null)
