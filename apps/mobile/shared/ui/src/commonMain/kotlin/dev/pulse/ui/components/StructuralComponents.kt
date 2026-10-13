package dev.pulse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import dev.pulse.core.data.SearchDefaults
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.ui.design.PulseTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.Node
import dev.pulse.dsl.PulseJson
import dev.pulse.dsl.RankStrategy
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope

/** `layout.stack`: vertical, keyed, animated. Reorders from "move the map to the top" glide. */
object LayoutStackComponent : PulseComponent {
    override val key = "layout.stack"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        // Clear the system bars plus the host chrome (back button above, intent bar below).
        // The keyboard shrinks the list itself (not just its padding), so a focused field counts as
        // hidden behind the keyboard and is scrolled into view (forms, "Other" category).
        val bars = WindowInsets.systemBars.asPaddingValues()
        LazyColumn(
            modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = bars.calculateTopPadding() + 72.dp,
                bottom = bars.calculateBottomPadding() + 132.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(node.children, key = { it.id }) { child ->
                scope.RenderNode(child, Modifier.animateItem())
            }
        }
    }
}

/** `hero.header`: the surface title, derived from the structured query (never model prose). */
object HeroHeaderComponent : PulseComponent {
    override val key = "hero.header"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val query = remember(node.props) {
            node.props["query"]?.let { PulseJson.decodeFromJsonElement(ListingQuery.serializer(), it) } ?: ListingQuery()
        }
        Column(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            BasicText(
                if (query.freshness == dev.pulse.dsl.Freshness.NEW) "SEARCH RECENTLY LAUNCHED" else "SEARCH AROUND",
                style = PulseType.overline.copy(color = Pulse.colors.accent),
            )
            BasicText(Format.headline(query, scope.languageTag), style = PulseType.display, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Format.chips(query).forEach { Pill(it) }
            }
        }
    }
}

/**
 * `list.cards`: renders its binding's template once per result. Never widens the search on its own:
 * when the radius comes back thin, it offers a wider search or another area, and the user decides.
 */
object ListCardsComponent : PulseComponent {
    override val key = "list.cards"

    private enum class Sort(val label: String) { BEST("Best match"), NEAREST("Nearest"), NEWEST("Newest") }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val bind = node.bind ?: return
        val template = bind.template ?: return
        val resolution = remember(bind, scope.dataVersion) { scope.resolve(bind) }
        var sort by rememberSaveable(bind) { mutableStateOf(Sort.BEST) }
        val items = when (sort) {
            Sort.BEST -> resolution.items
            Sort.NEAREST -> resolution.items.sortedBy { it.distanceMeters }
            Sort.NEWEST -> resolution.items.sortedByDescending { it.item.launchAtEpochMs ?: it.item.firstListedAtEpochMs }
        }
        val query = bind.query
        val near = query.near
        val radius = near?.radiusMeters ?: resolution.radiusMeters
        val status = scope.dataStatus
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val order = when (bind.rank.strategy) {
                RankStrategy.PERSONAL -> "best match"
                RankStrategy.SOONEST -> "soonest first"
                RankStrategy.DISTANCE -> "nearest first"
                RankStrategy.NEWEST -> "newest first"
                RankStrategy.POPULAR -> "most loved"
            }
            BasicText(
                "${items.size} ${if (items.size == 1) "result" else "results"} within ${Format.distance(radius.toDouble())} · ${if (sort == Sort.BEST) order else sort.label.lowercase()}".uppercase(),
                style = PulseType.overline,
                modifier = Modifier.padding(start = 4.dp).testTag("results-summary"),
            )
            if (items.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sort.entries.forEach { s -> PulseChip(s.label, "sort-${s.name.lowercase()}", selected = sort == s) { sort = s } }
                }
            }
            if (items.isEmpty() && status.loading) {
                BasicText("Looking around for places…", style = PulseType.body, modifier = Modifier.fillMaxWidth().glass().padding(20.dp))
            } else if (items.size < 3 && near != null) {
                ExpandPrompt(query, radius, items.size, status.notice, scope)
            }
            items.forEach { item -> scope.withItem(item).RenderNode(template) }
            if (items.any { it.item.id.startsWith("osm:") }) {
                // Required attribution for OpenStreetMap data (ODbL).
                BasicText("Places © OpenStreetMap contributors", style = PulseType.label, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }

    /** "Nothing within 6 km": search wider, or name an area. Explicit, never automatic. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ExpandPrompt(query: ListingQuery, radius: Int, count: Int, notice: String?, scope: RenderScope) {
        var area by rememberSaveable(query) { mutableStateOf("") }
        val wider = SearchDefaults.radiusChoicesKm.map { it * 1_000 }.filter { it > radius }.take(2)
            .ifEmpty { listOf(GeoFilter.MAX_RADIUS_METERS).filter { it > radius } }
        val what = query.text?.takeIf { query.categories.isEmpty() }
            ?: Format.headline(query.copy(near = null), scope.languageTag).let { if (it == "Everything around you") "places" else it.lowercase() }
        Column(Modifier.fillMaxWidth().glass().padding(18.dp).testTag("expand-prompt"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                when (count) {
                    0 -> if (query.text != null && query.categories.isEmpty()) "Nothing named “${query.text}” within ${Format.distance(radius.toDouble())}."
                    else "No $what within ${Format.distance(radius.toDouble())}."
                    1 -> "Only 1 result within ${Format.distance(radius.toDouble())}."
                    else -> "Only $count results within ${Format.distance(radius.toDouble())}."
                },
                style = PulseType.cardTitle,
            )
            notice?.let { BasicText(it, style = PulseType.label) }
            val nearest = remember(query, scope.dataVersion) { if (count < 3) scope.session.nearestBeyond(query) else null }
            if (nearest != null) {
                // Says where the closest match is; the user decides whether to look that far.
                val km = (nearest.distanceMeters / 1000).let { d -> SearchDefaults.radiusChoicesKm.firstOrNull { it >= d } ?: (kotlin.math.ceil(d).toInt()) }
                    .coerceAtMost(GeoFilter.MAX_RADIUS_METERS / 1000)
                BasicText(
                    "The nearest, ${nearest.item.title(scope.languageTag)}, is ${Format.distance(nearest.distanceMeters)} away.",
                    style = PulseType.body.copy(color = Pulse.colors.textPrimary),
                )
                PulseChip("Show results within $km km", "expand-nearest") {
                    scope.session.discover(query.copy(near = query.near?.copy(radiusMeters = km * 1000)), "$what within $km km")
                }
            }
            BasicText("Search further, or try another area?", style = PulseType.body)
            if (wider.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    wider.forEach { r ->
                        PulseChip("Within ${r / 1000} km", "expand-${r / 1000}") {
                            scope.session.discover(query.copy(near = query.near?.copy(radiusMeters = r)), "$what within ${r / 1000} km")
                        }
                    }
                }
            }
            PulseTextField(
                value = area,
                onValueChange = { area = it },
                placeholder = "Or type an area, e.g. Whitefield",
                onSubmit = { name -> scope.session.discover(query.copy(near = GeoFilter(GeoAnchor.Place(name.trim()), radius)), "$what in ${name.trim()}") },
                testTag = "expand-area",
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), strength = 1.6f).padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
    }
}

/** `list.videos`: horizontal trailer rail. */
object ListVideosComponent : PulseComponent {
    override val key = "list.videos"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val bind = node.bind ?: return
        val template = bind.template ?: return
        val items = remember(bind, scope.dataVersion) { scope.resolve(bind).items.filter { it.item.videoId != null } }
        if (items.isEmpty()) return
        Column(modifier.fillMaxWidth()) {
            BasicText("TRAILERS & PREVIEWS", style = PulseType.overline, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.item.id }) { scope.withItem(it).RenderNode(template) }
            }
        }
    }
}

/** `intent.suggestions`: shown when intent is unclear. Chips, never a chat reply. */
object IntentSuggestionsComponent : PulseComponent {
    override val key = "intent.suggestions"
    val examples = listOf(
        "Temples near me",
        "Fresh juice shops within 3 km",
        "Nearest IT park",
        "Salons in Whitefield",
        "Movies in nearby cinemas",
        "Upcoming dramas and events",
    )

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        Column(modifier.fillMaxWidth().glass().padding(18.dp)) {
            BasicText("TRY SEARCHING", style = PulseType.overline.copy(color = Pulse.colors.accent))
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                examples.forEach { ex ->
                    BasicText(
                        ex, style = PulseType.label.copy(color = Pulse.colors.textPrimary),
                        modifier = Modifier.glass(RoundedCornerShape(50), strength = 1.6f).clickable { scope.submitIntent(ex) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

/** Ships with the app. Remote config can disable keys; new keys arrive with app updates. */
val DefaultRegistry = dev.pulse.ui.registry.ComponentRegistry(
    listOf(
        LayoutStackComponent, HeroHeaderComponent, ListCardsComponent, ListVideosComponent, IntentSuggestionsComponent,
        GlassCardComponent, MapStackComponent, VideoTileComponent, ActionSheetComponent, GeometricButtonComponent,
        CategoryPickerComponent, RolePickerComponent, SavedShelfComponent,
        ClarifyComponent, ProfileEditorComponent, PageHeaderComponent, HistoryListComponent, SettingsPanelComponent,
        InfoPageComponent, BookingOptionsComponent, MerchantPublishComponent, MerchantPostsComponent,
    ),
)
