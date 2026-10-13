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
import dev.pulse.dsl.ThemePatch
import dev.pulse.dsl.ThemeSpec
import dev.pulse.core.data.MerchantDraft
import dev.pulse.core.data.MerchantPost
import dev.pulse.core.data.MerchantPosts
import dev.pulse.core.data.booking.BookingOption
import dev.pulse.core.prefs.AppSettings
import dev.pulse.core.prefs.HistoryEntry
import dev.pulse.core.prefs.UserProfile
import dev.pulse.ui.Screen

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

    /** Closest match outside the query's radius (for "nearest is 9 km away"), or null. */
    fun nearestBeyond(query: ListingQuery): ResolvedItem? = null

    /** Paint a new surface for a structured query (e.g. picker choice), keeping location intact. */
    fun discover(query: ListingQuery, label: String) {}

    fun setRole(role: UserRole) {}

    /** Whether a listing is on the user's shelf (drives the bookmark's on/off state). */
    fun isSaved(listingId: String): Boolean = false

    /** The user's saved places, newest first. */
    fun saved(): List<ListingItem> = emptyList()

    // ---- Navigation and feedback ----
    fun navigate(screen: Screen) {}
    fun toast(message: String) {}
    fun openUrl(url: String) {}
    val appVersion: String get() = ""

    // ---- Profile ----
    val profile: UserProfile? get() = null
    fun saveProfile(profile: UserProfile) {}
    fun profilePhoto(): ByteArray? = null
    fun setProfilePhoto(bytes: ByteArray?) {}
    /** Platform photo picker (downscaled bytes), or null if cancelled / unsupported. */
    suspend fun pickPhoto(): ByteArray? = null
    val canPickPhoto: Boolean get() = false
    fun signOut() {}
    fun deleteAllData() {}

    // ---- Search history ----
    fun history(): List<HistoryEntry> = emptyList()
    val historyEnabled: Boolean get() = true
    fun setHistoryEnabled(enabled: Boolean) {}
    fun removeHistory(text: String) {}
    fun clearHistory() {}

    // ---- Settings ----
    fun settings(): AppSettings = AppSettings()
    fun setDefaultRadiusKm(km: Int) {}
    /** False when the area name isn't known. */
    fun setManualArea(name: String?): Boolean = false
    val theme: ThemeSpec get() = ThemeSpec()
    fun setTheme(patch: ThemePatch) {}
    /** Human-readable location state ("Using your location", "Location not allowed"). */
    val locationStatus: String get() = ""
    fun requestLocation() {}
    val locationAllowed: Boolean get() = false

    // ---- Booking ----
    fun bookingTarget(): ResolvedItem? = null
    /** What the user originally searched for, kept on the booking page. */
    val bookingContext: String? get() = null
    fun bookingOptions(item: ListingItem): List<BookingOption> = emptyList()
    fun isInstalled(androidPackage: String): Boolean = false

    // ---- Merchant ----
    fun merchantPosts(): List<MerchantPost> = emptyList()
    fun publish(draft: MerchantDraft, image: ByteArray?): MerchantPosts.Outcome = MerchantPosts.Outcome.Rejected(listOf("Not available"))
    fun removePost(id: String) {}
    /** Merchant-uploaded image bytes by [ListingItem.imageKey]. */
    fun image(key: String): ByteArray? = null

    // ---- Help ----
    fun reportProblem(kind: String, details: String) {}

    object None : SessionActions
}

data class DataStatus(val loading: Boolean = false, val notice: String? = null)
