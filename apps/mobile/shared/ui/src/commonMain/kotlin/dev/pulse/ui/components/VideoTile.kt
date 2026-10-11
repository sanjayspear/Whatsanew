package dev.pulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pulse.core.data.ResolvedItem
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.Node
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reference component #4: trailer tile, muted by default, autoplay when focused. This file draws
 * the poster/glass chrome. The inline player is a platform view (`expect fun InlineVideo`:
 * android-youtube-player / WKWebView with the IFrame API) mounted only for the focused tile (P1),
 * so at most one decoder is ever alive, which keeps battery and thermals down.
 */
object VideoTileComponent : PulseComponent {
    override val key = "video.tile"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val r = scope.item ?: return
        if (r.item.videoId == null) return
        val muted = node.props["muted"]?.jsonPrimitive?.booleanOrNull ?: true
        val play = node.actions.firstOrNull { it.kind == ActionKind.PLAY_VIDEO }
        VideoTile(r, scope.languageTag, muted, onPlay = { play?.let { a -> scope.dispatch(a.copy(args = a.args.mapValues { (_, v) -> resolveArg(v, r) ?: v }), r) } }, modifier = modifier)
    }
}

@Composable
fun VideoTile(item: ResolvedItem, languageTag: String, muted: Boolean, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val t = Pulse.tokens
    val c = t.colors
    val h = item.item.id.hashCode()
    Box(modifier.width(228.dp).height(156.dp).glass(RoundedCornerShape(t.radiusLarge)).clickable(onClick = onPlay)) {
        Canvas(Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(t.radiusLarge - 6.dp))) {
            drawRect(Brush.linearGradient(listOf(c.accent2.copy(alpha = 0.85f), c.backgroundTop), Offset(0f, 0f), Offset(size.width, size.height)))
            // Stage-light beams for a cinematic poster feel.
            repeat(3) { i ->
                val x = size.width * (0.2f + 0.3f * i) + (h % 20)
                drawLine(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)),
                    Offset(x, 0f), Offset(x + size.width * 0.15f * (i - 1), size.height), strokeWidth = size.width * 0.12f,
                )
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)), startY = size.height * 0.4f))
        }
        Box(Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Image(PulseIcons.Play, "Play trailer", Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            BasicText(item.item.title(languageTag), style = PulseType.cardTitle.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(
                listOfNotNull(Format.eventTime(item.item), if (muted) "Muted preview" else null).joinToString("  ·  "),
                style = PulseType.label.copy(color = Color.White.copy(alpha = 0.8f)),
            )
        }
    }
}
