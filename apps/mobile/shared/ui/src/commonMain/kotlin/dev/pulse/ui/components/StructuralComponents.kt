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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
        val bars = WindowInsets.systemBars.asPaddingValues()
        LazyColumn(
            modifier.fillMaxSize(),
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
            BasicText("WHAT'S HAPPENING · BENGALURU", style = PulseType.overline.copy(color = Pulse.colors.accent))
            BasicText(Format.headline(query, scope.languageTag), style = PulseType.display, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Format.chips(query).forEach { Pill(it) }
            }
        }
    }
}

/** `list.cards`: renders its binding's template once per ranked result. */
object ListCardsComponent : PulseComponent {
    override val key = "list.cards"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val bind = node.bind ?: return
        val template = bind.template ?: return
        val items = remember(bind) { scope.resolve(bind) }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val order = when (bind.rank.strategy) {
                RankStrategy.PERSONAL -> "ranked for you"
                RankStrategy.SOONEST -> "soonest first"
                RankStrategy.DISTANCE -> "nearest first"
                RankStrategy.NEWEST -> "newest first"
                RankStrategy.POPULAR -> "most loved"
            }
            BasicText("${items.size} ${if (items.size == 1) "pick" else "picks"} · $order".uppercase(), style = PulseType.overline, modifier = Modifier.padding(start = 4.dp))
            if (items.isEmpty()) {
                val wider = (bind.query.near?.radiusMeters ?: 5_000) * 2
                BasicText(
                    "Nothing here yet. Tap to look within ${Format.distance(wider.toDouble())}",
                    style = PulseType.body,
                    modifier = Modifier.fillMaxWidth().glass().clickable { scope.submitIntent("within ${wider / 1000} km") }.padding(20.dp),
                )
            }
            items.forEach { item -> scope.withItem(item).RenderNode(template) }
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
        val items = remember(bind) { scope.resolve(bind).filter { it.item.videoId != null } }
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
        "New cafés in Koramangala within 3 km",
        "Theatre near Ravindra Kalakshetra this weekend",
        "Make it warm and quieter",
        "Move the map to the top",
    )

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        Column(modifier.fillMaxWidth().glass().padding(18.dp)) {
            BasicText("TRY ASKING", style = PulseType.overline.copy(color = Pulse.colors.accent))
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
    ),
)
