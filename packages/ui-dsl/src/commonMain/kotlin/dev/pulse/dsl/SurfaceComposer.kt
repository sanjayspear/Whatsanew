package dev.pulse.dsl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** Per-user layout preferences, persisted on-device and re-applied to every new surface. */
@Serializable
data class LayoutPrefs(
    val order: List<String> = DEFAULT_ORDER,
    val hidden: Set<String> = emptySet(),
) {
    fun apply(edit: LayoutEdit): LayoutPrefs = when (edit) {
        is LayoutEdit.Move -> {
            val rest = order - edit.role
            val at = if (edit.position < 0 || edit.position > rest.size) rest.size else edit.position
            copy(order = rest.toMutableList().apply { add(at, edit.role) })
        }
        is LayoutEdit.Hide -> copy(hidden = hidden + edit.role)
        is LayoutEdit.Show -> copy(hidden = hidden - edit.role)
    }

    companion object {
        val DEFAULT_ORDER = listOf(Roles.HEADER, Roles.MAP, Roles.RESULTS, Roles.VIDEOS)
    }
}

data class ComposeResult(
    val patch: UiPatch,
    val prefs: LayoutPrefs,
    /** For [Task.ACT]: the host dispatches this against what's on screen. */
    val action: ActionSpec? = null,
)

/**
 * Deterministic `IntentFrame -> UiPatch` expansion. Pure and sub-millisecond, it runs the same
 * on Android, iOS, desktop, and server-side (intent-service reuses it for parity tests).
 */
class SurfaceComposer(private val newSurfaceId: () -> String) {

    /**
     * @param profileTheme the user's saved theme, used for brand-new surfaces (a fresh surface
     *   still looks like "their" app).
     */
    fun compose(
        frame: IntentFrame,
        current: Surface?,
        prefs: LayoutPrefs,
        profileTheme: ThemeSpec = ThemeSpec(),
    ): ComposeResult {
        val base = when (frame.task) {
            Task.DISCOVER -> discover(frame.query ?: ListingQuery(), current, prefs, profileTheme)
            Task.REFINE -> refine(frame.query ?: ListingQuery(), current, prefs, profileTheme)
            Task.CUSTOMIZE_THEME -> ComposeResult(
                UiPatch(listOfNotNull(frame.theme?.let(UiOp::UpdateTheme)), Persistence.PROFILE),
                prefs,
            )
            Task.CUSTOMIZE_LAYOUT -> customizeLayout(frame.layout, current, prefs)
            Task.ACT -> ComposeResult(UiPatch(emptyList()), prefs, frame.action)
            Task.UNKNOWN -> suggest(current, prefs)
        }
        return if (frame.extraOps.isEmpty()) base
        else base.copy(patch = base.patch.copy(ops = base.patch.ops + frame.extraOps))
    }

    private fun discover(query: ListingQuery, current: Surface?, prefs: LayoutPrefs, profileTheme: ThemeSpec): ComposeResult {
        val surface = Surface(
            id = newSurfaceId(),
            root = buildRoot(query.withDefaultAnchor(), prefs),
            theme = current?.theme ?: profileTheme,
        )
        return ComposeResult(UiPatch(listOf(UiOp.ReplaceSurface(surface))), prefs)
    }

    private fun refine(delta: ListingQuery, current: Surface?, prefs: LayoutPrefs, profileTheme: ThemeSpec): ComposeResult {
        if (current == null) return discover(delta, null, prefs, profileTheme)
        val ops = current.root.walk()
            .filter { it.bind != null && it.id != TEMPLATE_CARD && it.id != TEMPLATE_VIDEO }
            .map { node ->
                val bind = node.bind!!
                UiOp.UpdateNode(node.id, bind = bind.copy(query = bind.query.refinedBy(delta)))
            }
            .toList<UiOp>()
        val headerOps = current.root.findByRole(Roles.HEADER)?.let { header ->
            val old = header.props["query"]?.let { PulseJson.decodeFromJsonElement(ListingQuery.serializer(), it) }
            val merged = old?.refinedBy(delta) ?: delta
            listOf(UiOp.UpdateNode(header.id, props = queryProps(merged)))
        }.orEmpty()
        return ComposeResult(UiPatch(ops + headerOps), prefs)
    }

    private fun customizeLayout(edits: List<LayoutEdit>, current: Surface?, prefs: LayoutPrefs): ComposeResult {
        val newPrefs = edits.fold(prefs) { acc, edit -> acc.apply(edit) }
        if (current == null) return ComposeResult(UiPatch(emptyList(), Persistence.PROFILE), newPrefs)

        val needsRebuild = edits.any { it is LayoutEdit.Show && current.root.findByRole(it.role) == null }
        if (needsRebuild) {
            val query = currentQuery(current) ?: ListingQuery()
            val rebuilt = current.copy(root = buildRoot(query.withDefaultAnchor(), newPrefs))
            return ComposeResult(UiPatch(listOf(UiOp.ReplaceSurface(rebuilt)), Persistence.PROFILE), newPrefs)
        }

        val ops = edits.mapNotNull { edit ->
            val node = current.root.findByRole(edit.role) ?: return@mapNotNull null
            val parent = current.root.parentOf(node.id) ?: return@mapNotNull null
            when (edit) {
                is LayoutEdit.Move -> UiOp.MoveNode(node.id, parent.id, edit.position)
                is LayoutEdit.Hide -> UiOp.RemoveNode(node.id)
                is LayoutEdit.Show -> null
            }
        }
        return ComposeResult(UiPatch(ops, Persistence.PROFILE), newPrefs)
    }

    private fun suggest(current: Surface?, prefs: LayoutPrefs): ComposeResult {
        val chips = Node(SUGGESTIONS_ID, "intent.suggestions", role = Roles.SUGGESTIONS)
        if (current == null) {
            val root = Node(ROOT_ID, "layout.stack", role = Roles.ROOT, children = listOf(chips))
            return ComposeResult(UiPatch(listOf(UiOp.ReplaceSurface(Surface(newSurfaceId(), root)))), prefs)
        }
        val ops = buildList {
            if (current.root.findById(SUGGESTIONS_ID) != null) add(UiOp.RemoveNode(SUGGESTIONS_ID))
            add(UiOp.InsertNode(current.root.id, chips, index = 0))
        }
        return ComposeResult(UiPatch(ops), prefs)
    }

    private fun buildRoot(query: ListingQuery, prefs: LayoutPrefs): Node {
        val eventLike = query.isEventLike()
        val sections = buildMap {
            put(Roles.HEADER, Node("header", "hero.header", role = Roles.HEADER, props = queryProps(query)))
            if (query.near != null) {
                put(
                    Roles.MAP,
                    Node(
                        "map", "map.stack", role = Roles.MAP,
                        bind = Binding(query, RankSpec(RankStrategy.DISTANCE), limit = Binding.MAX_LIMIT),
                    ),
                )
            }
            put(
                Roles.RESULTS,
                Node(
                    "results", "list.cards", role = Roles.RESULTS,
                    bind = Binding(
                        query,
                        RankSpec(
                            when {
                                query.freshness == Freshness.NEW -> RankStrategy.NEWEST
                                eventLike -> RankStrategy.SOONEST
                                else -> RankStrategy.PERSONAL
                            },
                        ),
                        limit = 20,
                        template = cardTemplate(),
                    ),
                ),
            )
            if (eventLike) {
                put(
                    Roles.VIDEOS,
                    Node(
                        "videos", "list.videos", role = Roles.VIDEOS,
                        bind = Binding(query, RankSpec(RankStrategy.POPULAR), limit = 8, template = videoTemplate()),
                    ),
                )
            }
        }
        val ordered = (prefs.order + sections.keys.filterNot { it in prefs.order })
            .filterNot { it in prefs.hidden }
            .mapNotNull { sections[it] }
        // Vague request ("show me something nearby"): lead with a category picker. It always sits
        // on top, independent of layout prefs, because it is a question for the user rather than content.
        val picker = if (query.isAmbiguous()) {
            listOf(Node(PICKER_ID, "intent.category_picker", role = Roles.PICKER, props = queryProps(query)))
        } else emptyList()
        return Node(ROOT_ID, "layout.stack", role = Roles.ROOT, children = picker + ordered)
    }

    private fun cardTemplate() = Node(
        id = TEMPLATE_CARD,
        component = "glass.card",
        actions = buildList {
            // Shown only for listings with a real booking destination: `$item.bookable` resolves to
            // null otherwise, and the renderer drops actions whose item arguments are missing.
            add(ActionSpec(ActionKind.BOOK, mapOf("listingId" to "\$item.id", "bookable" to "\$item.bookable")))
            add(ActionSpec(ActionKind.NAVIGATE, mapOf("lat" to "\$item.lat", "lng" to "\$item.lng")))
            add(ActionSpec(ActionKind.CALL, mapOf("phone" to "\$item.phone")))
            add(ActionSpec(ActionKind.SAVE, mapOf("listingId" to "\$item.id")))
            add(ActionSpec(ActionKind.SHARE, mapOf("listingId" to "\$item.id")))
        },
    )

    private fun videoTemplate() = Node(
        id = TEMPLATE_VIDEO,
        component = "video.tile",
        props = JsonObject(mapOf("autoplayOnFocus" to JsonPrimitive(true), "muted" to JsonPrimitive(true))),
        actions = listOf(ActionSpec(ActionKind.PLAY_VIDEO, mapOf("videoId" to "\$item.videoId"))),
    )

    private fun queryProps(query: ListingQuery) =
        JsonObject(mapOf("query" to PulseJson.encodeToJsonElement(query).jsonObject))

    private fun currentQuery(surface: Surface): ListingQuery? =
        surface.root.findByRole(Roles.RESULTS)?.bind?.query

    companion object {
        const val ROOT_ID = "root"
        const val SUGGESTIONS_ID = "suggestions"
        const val TEMPLATE_CARD = "card"
        const val TEMPLATE_VIDEO = "video"
        const val PICKER_ID = "picker"
    }
}

/** "What's happening around me": an unanchored query means "near my current location". */
internal fun ListingQuery.withDefaultAnchor(): ListingQuery =
    if (near != null) this else copy(near = GeoFilter(GeoAnchor.Here))

private val EVENT_ROOTS = listOf("arts.", "events.", "music.", "sports.", "comedy.")

/** Nothing narrows *what* the user wants, so a category question is more useful than a guess. */
fun ListingQuery.isAmbiguous(): Boolean =
    categories.isEmpty() && text == null && freshness == null && time == null && priceLevelMax == null

/** Time-bound content gets showtimes, booking, and trailers. */
internal fun ListingQuery.isEventLike(): Boolean =
    time != null || categories.any { c -> EVENT_ROOTS.any(c::startsWith) }
