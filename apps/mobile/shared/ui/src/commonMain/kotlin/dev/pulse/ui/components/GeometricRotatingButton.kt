package dev.pulse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.pulse.dsl.Node
import dev.pulse.ui.components.Cuboctahedron.drawCuboctahedron
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.neumorphic
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reference component #1: a neumorphic button visually dominated by a slowly rotating,
 * translucent cuboctahedron. The rotation period scales with the theme's motion token, so
 * "make it quieter" literally slows it down, and motion 0 freezes it (reduced-motion).
 */
@Composable
fun GeometricRotatingButton(
    label: String,
    sublabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    phaseDeg: Float = 0f,
) {
    val tokens = Pulse.tokens
    val colors = tokens.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val periodMs = tokens.durationMs(28_000).coerceAtLeast(1)
    val transition = rememberInfiniteTransition(label = "solid")
    val yaw by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "yaw")
    val bob by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(periodMs / 3, easing = LinearEasing), RepeatMode.Reverse), label = "bob")
    val still = tokens.motionScale <= 0.01f

    Box(
        modifier
            .neumorphic(RoundedCornerShape(tokens.radiusLarge), pressed = pressed)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * 0.72f, size.height * 0.40f)
            drawCircle(
                Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.35f), Color.Transparent), c, size.minDimension * 0.55f),
                size.minDimension * 0.55f, c,
            )
            drawCuboctahedron(
                yawDeg = (if (still) 35f else yaw) + phaseDeg,
                pitchDeg = 24f + if (still) 0f else bob * 8f,
                squareTint = colors.accent,
                triangleTint = colors.accent2,
                edge = Color.White,
                scale = 0.24f,
                center = c,
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            BasicText(label, style = PulseType.display)
            BasicText(sublabel, style = PulseType.body, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** DSL-addressable variant (`geo.button`), so AI surfaces can place hero CTAs too. */
object GeometricButtonComponent : PulseComponent {
    override val key = "geo.button"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val label = node.props["label"]?.jsonPrimitive?.contentOrNull ?: return
        val intent = node.props["intent"]?.jsonPrimitive?.contentOrNull
        GeometricRotatingButton(
            label = label,
            sublabel = node.props["sublabel"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            onClick = { intent?.let(scope.submitIntent) },
            modifier = modifier,
        )
    }
}
