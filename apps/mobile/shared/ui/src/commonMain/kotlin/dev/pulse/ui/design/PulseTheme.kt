package dev.pulse.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pulse.dsl.Mood
import dev.pulse.dsl.ThemeSpec

/**
 * Pulse design tokens. Every visual value derives from a DSL [ThemeSpec], so one AI theme
 * mutation ("warm and quieter") re-skins every surface consistently. Components never hardcode
 * colors, radii or durations.
 */
@Immutable
data class PulseColors(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val orbA: Color,
    val orbB: Color,
    /** Neumorphic base: raised controls are this color with paired light/dark shadows. */
    val surface: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val accent: Color,
    val accent2: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isDark: Boolean,
)

@Immutable
data class PulseTokens(
    val colors: PulseColors,
    /** Radius scale tied to the geometric motif: cards use [radiusLarge], controls [radiusSmall]. */
    val radiusLarge: Dp,
    val radiusSmall: Dp,
    val elevation: Dp,
    val glassAlpha: Float,
    val typeScale: Float,
    val motionScale: Float,
) {
    /** Duration helper honouring the motion scale (0 = reduced motion). */
    fun durationMs(base: Int): Int = if (motionScale <= 0.01f) 0 else (base / motionScale).toInt()

    companion object {
        fun from(spec: ThemeSpec): PulseTokens = PulseTokens(
            colors = palette(spec),
            radiusLarge = (28 * spec.radiusScale).dp,
            radiusSmall = (16 * spec.radiusScale).dp,
            elevation = (10 * spec.elevationScale).dp,
            glassAlpha = (0.10f + 0.06f * spec.blurScale).coerceIn(0.06f, 0.30f),
            typeScale = spec.typeScale,
            motionScale = spec.motionScale,
        )
    }
}

val LocalPulse = staticCompositionLocalOf { PulseTokens.from(ThemeSpec()) }

object Pulse {
    val tokens: PulseTokens
        @Composable @ReadOnlyComposable get() = LocalPulse.current
    val colors: PulseColors
        @Composable @ReadOnlyComposable get() = LocalPulse.current.colors
}

/** Animates palette changes so AI theme mutations cross-fade instead of snapping. */
@Composable
fun PulseTheme(spec: ThemeSpec, content: @Composable () -> Unit) {
    val target = PulseTokens.from(spec)
    val ms = target.durationMs(700)
    @Composable
    fun anim(c: Color): Color {
        val v by animateColorAsState(c, tween(ms))
        return v
    }
    val c = target.colors
    val colors = c.copy(
        backgroundTop = anim(c.backgroundTop),
        backgroundBottom = anim(c.backgroundBottom),
        orbA = anim(c.orbA),
        orbB = anim(c.orbB),
        surface = anim(c.surface),
        accent = anim(c.accent),
        accent2 = anim(c.accent2),
        glassFill = anim(c.glassFill),
    )
    CompositionLocalProvider(LocalPulse provides target.copy(colors = colors), content = content)
}

private const val DEFAULT_ACCENT = "#7C9CFF"

private fun palette(spec: ThemeSpec): PulseColors {
    val base = when (spec.mood) {
        Mood.DUSK -> PulseColors(
            backgroundTop = Color(0xFF0D1330), backgroundBottom = Color(0xFF1A1140),
            orbA = Color(0xFF3B4CFF), orbB = Color(0xFFB04DFF),
            surface = Color(0xFF191E3E), shadowDark = Color(0xFF070A1C), shadowLight = Color(0xFF2B3266),
            glassFill = Color(0xFFFFFFFF), glassBorder = Color(0x40FFFFFF),
            accent = Color(0xFF8FA8FF), accent2 = Color(0xFFD09BFF),
            textPrimary = Color(0xFFF3F4FF), textSecondary = Color(0xFFA8AFD8), isDark = true,
        )
        Mood.WARM -> PulseColors(
            backgroundTop = Color(0xFF26130F), backgroundBottom = Color(0xFF3B1D16),
            orbA = Color(0xFFFF7A45), orbB = Color(0xFFFFC36B),
            surface = Color(0xFF3A231D), shadowDark = Color(0xFF1A0B08), shadowLight = Color(0xFF5A3A30),
            glassFill = Color(0xFFFFF1E6), glassBorder = Color(0x40FFE3CC),
            accent = Color(0xFFFFB27A), accent2 = Color(0xFFFF8A6B),
            textPrimary = Color(0xFFFFF4EC), textSecondary = Color(0xFFD9B6A4), isDark = true,
        )
        Mood.CALM -> PulseColors(
            backgroundTop = Color(0xFF162019), backgroundBottom = Color(0xFF212C27),
            orbA = Color(0xFF5C8D78), orbB = Color(0xFFA6B88F),
            surface = Color(0xFF223029), shadowDark = Color(0xFF0B110E), shadowLight = Color(0xFF34473D),
            glassFill = Color(0xFFF2FFF7), glassBorder = Color(0x33E6FFF0),
            accent = Color(0xFFA5D3BA), accent2 = Color(0xFFD3DDB8),
            textPrimary = Color(0xFFEFF7F2), textSecondary = Color(0xFFA9BDB2), isDark = true,
        )
        Mood.VIVID -> PulseColors(
            backgroundTop = Color(0xFF12082E), backgroundBottom = Color(0xFF052A3B),
            orbA = Color(0xFF00E5FF), orbB = Color(0xFFFF3DB4),
            surface = Color(0xFF1A1440), shadowDark = Color(0xFF07031A), shadowLight = Color(0xFF2E2766),
            glassFill = Color(0xFFFFFFFF), glassBorder = Color(0x55FFFFFF),
            accent = Color(0xFF4DF0FF), accent2 = Color(0xFFFF5CC8),
            textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFB9C3F0), isDark = true,
        )
        Mood.NOIR -> PulseColors(
            backgroundTop = Color(0xFF0A0A0C), backgroundBottom = Color(0xFF151519),
            orbA = Color(0xFF3A3A44), orbB = Color(0xFF26262E),
            surface = Color(0xFF17171B), shadowDark = Color(0xFF000000), shadowLight = Color(0xFF2A2A31),
            glassFill = Color(0xFFFFFFFF), glassBorder = Color(0x30FFFFFF),
            accent = Color(0xFFEDEDF0), accent2 = Color(0xFF9A9AA4),
            textPrimary = Color(0xFFF5F5F7), textSecondary = Color(0xFF9C9CA6), isDark = true,
        )
        Mood.DAYLIGHT -> PulseColors(
            backgroundTop = Color(0xFFEEF1F8), backgroundBottom = Color(0xFFE1E6F2),
            orbA = Color(0xFF9DB0FF), orbB = Color(0xFFFFC2E2),
            surface = Color(0xFFE8ECF5), shadowDark = Color(0xFFB8C0D6), shadowLight = Color(0xFFFFFFFF),
            glassFill = Color(0xFFFFFFFF), glassBorder = Color(0x80FFFFFF),
            accent = Color(0xFF4766F0), accent2 = Color(0xFFB0459A),
            textPrimary = Color(0xFF1A1F33), textSecondary = Color(0xFF5B6382), isDark = false,
        )
    }
    val accent = if (spec.accentHex.equals(DEFAULT_ACCENT, ignoreCase = true)) base.accent else parseHex(spec.accentHex) ?: base.accent
    return base.copy(
        accent = accent.saturate(spec.saturation),
        accent2 = base.accent2.saturate(spec.saturation),
        orbA = base.orbA.saturate(spec.saturation),
        orbB = base.orbB.saturate(spec.saturation),
    )
}

private fun Color.saturate(amount: Float): Color {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    val gray = Color(luminance, luminance, luminance, alpha)
    return if (amount <= 1f) lerp(gray, this, amount)
    else Color((red + (red - luminance) * (amount - 1)).coerceIn(0f, 1f), (green + (green - luminance) * (amount - 1)).coerceIn(0f, 1f), (blue + (blue - luminance) * (amount - 1)).coerceIn(0f, 1f), alpha)
}

private fun parseHex(hex: String): Color? = hex.removePrefix("#").toLongOrNull(16)?.let { Color(0xFF000000 or it) }
