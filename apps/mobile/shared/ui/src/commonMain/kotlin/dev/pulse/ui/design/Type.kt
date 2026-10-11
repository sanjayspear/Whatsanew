package dev.pulse.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Type ramp scaled by the DSL `typeScale` token (clamped 0.85..1.5 for accessibility). */
object PulseType {
    private fun scaled(size: Int, scale: Float): TextUnit = (size * scale).sp

    val display: TextStyle @Composable @ReadOnlyComposable get() = style(34, FontWeight.Bold, -0.02f)
    val title: TextStyle @Composable @ReadOnlyComposable get() = style(22, FontWeight.SemiBold, -0.01f)
    val cardTitle: TextStyle @Composable @ReadOnlyComposable get() = style(17, FontWeight.SemiBold, 0f)
    val body: TextStyle @Composable @ReadOnlyComposable get() = style(14, FontWeight.Normal, 0f, secondary = true)
    val label: TextStyle @Composable @ReadOnlyComposable get() = style(12, FontWeight.Medium, 0.02f, secondary = true)
    val overline: TextStyle @Composable @ReadOnlyComposable get() = style(11, FontWeight.SemiBold, 0.12f, secondary = true)

    @Composable
    @ReadOnlyComposable
    private fun style(size: Int, weight: FontWeight, tracking: Float, secondary: Boolean = false): TextStyle {
        val t = Pulse.tokens
        return TextStyle(
            fontSize = scaled(size, t.typeScale),
            fontWeight = weight,
            letterSpacing = tracking.em,
            color = if (secondary) t.colors.textSecondary else t.colors.textPrimary,
        )
    }
}
