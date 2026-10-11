package dev.pulse.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Neumorphic (soft-embossed) surface for primary actions: the base color plus a light shadow
 * up-left and a dark shadow down-right. [pressed] flips to inner shadows (debossed).
 */
@Composable
fun Modifier.neumorphic(shape: Shape = RoundedCornerShape(Pulse.tokens.radiusSmall), pressed: Boolean = false): Modifier {
    val t = Pulse.tokens
    val c = t.colors
    val e = t.elevation
    return if (!pressed) {
        this
            .dropShadow(shape, Shadow(radius = e * 1.4f, color = c.shadowLight.copy(alpha = if (c.isDark) 0.55f else 0.9f), offset = DpOffset(-e / 2, -e / 2)))
            .dropShadow(shape, Shadow(radius = e * 1.4f, color = c.shadowDark.copy(alpha = if (c.isDark) 0.9f else 0.7f), offset = DpOffset(e / 2, e / 2)))
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.surface.lighten(0.04f), c.surface.darken(0.04f))))
    } else {
        this
            .clip(shape)
            .background(c.surface)
            .innerShadow(shape, Shadow(radius = e, color = c.shadowDark, offset = DpOffset(e / 3, e / 3)))
            .innerShadow(shape, Shadow(radius = e, color = c.shadowLight.copy(alpha = 0.6f), offset = DpOffset(-e / 3, -e / 3)))
    }
}

/**
 * Glassmorphic panel for overlays and media: translucent fill, a top-left specular sheen and a
 * hairline border. Over the ambient orbs it reads as frosted glass on every platform with no
 * runtime blur cost. Real backdrop blur (Haze) is reserved for the floating intent bar,
 * where content scrolls underneath it.
 */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(Pulse.tokens.radiusLarge), strength: Float = 1f): Modifier {
    val t = Pulse.tokens
    val c = t.colors
    val a = t.glassAlpha * strength
    return this
        .dropShadow(shape, Shadow(radius = 24.dp, color = Color.Black.copy(alpha = if (c.isDark) 0.35f else 0.12f), offset = DpOffset(0.dp, 10.dp)))
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(c.glassFill.copy(alpha = a * 1.6f), c.glassFill.copy(alpha = a * 0.5f)),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        )
        .border(1.dp, Brush.linearGradient(listOf(c.glassBorder, c.glassBorder.copy(alpha = 0.05f))), shape)
}

/** The ambient backdrop every surface sits on: mood gradient plus two soft light orbs. */
@Composable
fun PulseBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Pulse.colors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.backgroundTop, c.backgroundBottom)))
            .drawBehind {
                val r = max(size.width, size.height)
                drawCircle(
                    Brush.radialGradient(listOf(c.orbA.copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width * 0.1f, size.height * 0.12f), radius = r * 0.55f),
                    radius = r * 0.55f, center = Offset(size.width * 0.1f, size.height * 0.12f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(c.orbB.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.7f), radius = r * 0.6f),
                    radius = r * 0.6f, center = Offset(size.width * 0.95f, size.height * 0.7f),
                )
            },
        content = content,
    )
}

fun Color.lighten(f: Float) = Color((red + (1 - red) * f), (green + (1 - green) * f), (blue + (1 - blue) * f), alpha)
fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
