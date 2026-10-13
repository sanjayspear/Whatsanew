package dev.pulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pulse.ui.components.Cuboctahedron.drawCuboctahedron
import dev.pulse.ui.design.Pulse

/** The Search Around mark: the app's cuboctahedron on an accent tile. */
@Composable
fun BrandMark(size: Int, modifier: Modifier = Modifier) {
    val c = Pulse.colors
    Canvas(modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp))) {
        drawRect(Brush.linearGradient(listOf(c.accent, c.accent2), Offset.Zero, Offset(this.size.width, this.size.height)))
        drawCuboctahedron(yawDeg = 32f, pitchDeg = 24f, squareTint = Color.White, triangleTint = Color.White, edge = Color.White, scale = 0.36f)
    }
}
