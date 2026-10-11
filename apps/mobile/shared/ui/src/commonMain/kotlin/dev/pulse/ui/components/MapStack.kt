package dev.pulse.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ResolvedItem
import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.Node
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Reference component #3: radius ring + clustered pins.
 *
 * This is the *offline schematic layer*: it needs no tiles, renders instantly, and is what
 * users see with zero network. In P1 a MapLibre Compose view (`expect/actual` over MapLibre
 * Native Android/iOS, vector tiles cached offline per region) is layered underneath. The pins,
 * ring and clustering here stay as the overlay, so the component contract doesn't change.
 */
object MapStackComponent : PulseComponent {
    override val key = "map.stack"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val bind = node.bind ?: return
        val resolution = remember(bind, scope.dataVersion) { scope.resolve(bind) }
        val near = bind.query.near
        MapStack(
            anchor = scope.anchorOf(near?.anchor),
            anchorLabel = (near?.anchor as? GeoAnchor.Place)?.name ?: "You",
            radiusMeters = resolution.radiusMeters,
            items = resolution.items,
            modifier = modifier,
        )
    }
}

@Composable
fun MapStack(anchor: GeoCoordinate, anchorLabel: String, radiusMeters: Int, items: List<ResolvedItem>, modifier: Modifier = Modifier) {
    val t = Pulse.tokens
    val c = t.colors
    val measurer = rememberTextMeasurer()
    val clusterStyle = PulseType.overline.copy(color = Color.White, fontSize = 11.sp)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(t.durationMs(2_400).coerceAtLeast(1)), RepeatMode.Restart), label = "ring",
    )
    Box(modifier.fillMaxWidth().height(250.dp).glass(RoundedCornerShape(t.radiusLarge))) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2 + 8.dp.toPx())
            val ringPx = size.minDimension * 0.40f
            val pxPerMeter = ringPx / radiusMeters

            // Procedural street fabric, seeded by the anchor so it is stable per place.
            val rnd = Random((anchor.lat * 1e4).roundToInt() * 31 + (anchor.lng * 1e4).roundToInt())
            repeat(14) {
                val vertical = rnd.nextBoolean()
                val p = rnd.nextFloat()
                val start = if (vertical) Offset(size.width * p, 0f) else Offset(0f, size.height * p)
                val end = if (vertical) Offset(size.width * (p + rnd.nextFloat() * 0.2f - 0.1f), size.height) else Offset(size.width, size.height * (p + rnd.nextFloat() * 0.2f - 0.1f))
                drawLine(c.textSecondary.copy(alpha = 0.10f), start, end, strokeWidth = (1 + rnd.nextInt(3)).dp.toPx())
            }

            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.18f), Color.Transparent), center, ringPx), ringPx, center)
            drawCircle(c.accent.copy(alpha = 0.55f), ringPx, center, style = Stroke(1.5.dp.toPx()))
            drawCircle(c.accent.copy(alpha = 0.25f), ringPx / 2, center, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            drawCircle(c.accent.copy(alpha = 0.35f * (1 - pulse)), ringPx * pulse, center, style = Stroke(2.dp.toPx()))

            // Equirectangular projection around the anchor, accurate to well under 1% within 50 km.
            val metersPerDegLat = 111_320.0
            val metersPerDegLng = 111_320.0 * cos(anchor.lat * PI / 180)
            val points = items.map { r ->
                Offset(
                    center.x + ((r.item.lng - anchor.lng) * metersPerDegLng * pxPerMeter).toFloat(),
                    center.y - ((r.item.lat - anchor.lat) * metersPerDegLat * pxPerMeter).toFloat(),
                )
            }
            // Greedy screen-space clustering (28 dp cells).
            val cell = 28.dp.toPx()
            val clusters = points.groupBy { (it.x / cell).toInt() to (it.y / cell).toInt() }.values
            clusters.forEach { group ->
                val p = Offset(group.map { it.x }.average().toFloat(), group.map { it.y }.average().toFloat())
                val r = if (group.size > 1) 13.dp.toPx() else 8.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(c.accent2.copy(alpha = 0.6f), Color.Transparent), p, r * 2.4f), r * 2.4f, p)
                drawCircle(c.accent2, r, p)
                drawCircle(Color.White, r, p, style = Stroke(1.5.dp.toPx()))
                if (group.size > 1) {
                    val label = measurer.measure(group.size.toString(), clusterStyle)
                    drawText(label, topLeft = Offset(p.x - label.size.width / 2, p.y - label.size.height / 2))
                }
            }
            drawCircle(Color.White, 6.dp.toPx(), center)
            drawCircle(c.accent, 4.dp.toPx(), center)
        }
        Row(Modifier.align(Alignment.TopStart).padding(14.dp)) {
            Pill("$anchorLabel · ${Format.distance(radiusMeters.toDouble())}")
        }
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            Pill("${items.size} ${if (items.size == 1) "spot" else "spots"}", accent = true)
        }
    }
}

@Composable
internal fun Pill(text: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    val c = Pulse.colors
    BasicText(
        text,
        style = PulseType.label.copy(color = if (accent) c.accent else c.textPrimary),
        modifier = modifier
            .background(c.backgroundTop.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
