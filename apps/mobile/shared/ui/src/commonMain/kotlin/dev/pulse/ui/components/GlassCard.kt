package dev.pulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pulse.core.data.ResolvedItem
import dev.pulse.core.signals.Signal
import dev.pulse.dsl.Node
import dev.pulse.ui.components.Cuboctahedron.drawCuboctahedron
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope

/**
 * Reference component #2: listing preview on frosted glass with procedural art, key facts and an
 * actionable neumorphic sheet. Rendered once per bound result (`glass.card` is a binding template).
 */
object GlassCardComponent : PulseComponent {
    override val key = "glass.card"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val r = scope.item ?: return
        val image = rememberBitmap(remember(r.item.imageKey) { r.item.imageKey?.let(scope.session::image) })
        GlassCard(
            item = r,
            image = image,
            languageTag = scope.languageTag,
            nowMs = scope.nowMs(),
            onOpen = { scope.signal(Signal(Signal.Kind.TAP, r.item.category, r.item.id, scope.nowMs())) },
            actions = { ActionSheet(node.actions, r, { scope.dispatch(it, r) }, isActive = { scope.isActive(it) }) },
            modifier = modifier,
        )
    }
}

@Composable
fun GlassCard(
    item: ResolvedItem,
    languageTag: String,
    nowMs: Long,
    onOpen: () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
) {
    val t = Pulse.tokens
    val listing = item.item
    Row(
        modifier
            .fillMaxWidth()
            .height(168.dp)
            .glass(RoundedCornerShape(t.radiusLarge))
            .clickable(onClick = onOpen)
            .padding(12.dp),
    ) {
        val launch = Format.launchLine(listing, nowMs)
        if (image != null) {
            Box(Modifier.width(104.dp).fillMaxHeight().clip(RoundedCornerShape(t.radiusSmall))) {
                Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        } else {
            ArtTile(listing.category, listing.id, isNew = launch != null, modifier = Modifier.width(104.dp).fillMaxHeight())
        }
        Column(Modifier.padding(start = 14.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                BasicText(
                    Format.category(listing.category, languageTag).uppercase(),
                    style = PulseType.overline.copy(color = t.colors.accent),
                )
                BasicText(listing.title(languageTag), style = PulseType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (launch != null) {
                    // Distinguishes newly launched content from long-established listings.
                    BasicText(launch, style = PulseType.label.copy(color = t.colors.accent2), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
                (listing.taglineFor(languageTag) ?: listing.address)?.let {
                    BasicText(it, style = PulseType.body, maxLines = if (launch != null) 1 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
            BasicText(
                listOfNotNull(
                    Format.eventTime(listing),
                    Format.distance(item.distanceMeters),
                    listing.rating?.let { "★ $it" },
                    Format.price(listing),
                ).joinToString("  ·  "),
                style = PulseType.label,
                maxLines = 1,
            )
            actions()
        }
    }
}

/** Offline-safe "hero image": a category-tinted gradient carrying the geometric motif. */
@Composable
internal fun ArtTile(category: String, seed: String, isNew: Boolean, modifier: Modifier = Modifier) {
    val c = Pulse.colors
    val h = seed.hashCode()
    val (a, b) = when (category.substringBefore('.')) {
        "food" -> c.accent2 to c.orbA
        "arts", "music", "comedy" -> c.accent to c.accent2
        "fitness" -> c.orbA to c.accent
        else -> c.orbB to c.accent
    }
    Box(modifier.clip(RoundedCornerShape(Pulse.tokens.radiusSmall))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(a.copy(alpha = 0.95f), b.copy(alpha = 0.55f)), Offset.Zero, Offset(size.width, size.height)))
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.1f), size.maxDimension * 0.8f))
            drawCuboctahedron(
                yawDeg = (h % 360).toFloat(), pitchDeg = 20f + (h % 25),
                squareTint = Color.White, triangleTint = Color.White, edge = Color.White,
                scale = 0.42f, center = Offset(size.width * 0.62f, size.height * 0.62f),
            )
        }
        if (isNew) {
            BasicText(
                "NEW",
                style = PulseType.overline.copy(color = Color.White),
                modifier = Modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
