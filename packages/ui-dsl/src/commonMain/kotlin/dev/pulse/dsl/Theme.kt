package dev.pulse.dsl

import kotlinx.serialization.Serializable

/**
 * Absolute theme tokens for a surface. Components never read raw colors; they derive
 * neumorphic / glass styling from these tokens, so a single mood change re-skins everything.
 */
@Serializable
data class ThemeSpec(
    val mood: Mood = Mood.DUSK,
    /** `#RRGGBB`. */
    val accentHex: String = "#7C9CFF",
    val radiusScale: Float = 1f,
    val elevationScale: Float = 1f,
    val blurScale: Float = 1f,
    val typeScale: Float = 1f,
    val motionScale: Float = 1f,
    val saturation: Float = 1f,
) {
    /** Applies a patch with hard clamps, so no model output can make the UI unusable. */
    fun apply(patch: ThemePatch): ThemeSpec = ThemeSpec(
        mood = patch.mood ?: mood,
        accentHex = patch.accentHex?.takeIf(::isHexColor) ?: accentHex,
        radiusScale = patch.radiusScale?.let { ThemeBounds.radius.clamp(it) } ?: radiusScale,
        elevationScale = patch.elevationScale?.let { ThemeBounds.elevation.clamp(it) } ?: elevationScale,
        blurScale = patch.blurScale?.let { ThemeBounds.blur.clamp(it) } ?: blurScale,
        typeScale = patch.typeScale?.let { ThemeBounds.type.clamp(it) } ?: typeScale,
        motionScale = patch.motionScale?.let { ThemeBounds.motion.clamp(it) } ?: motionScale,
        saturation = patch.saturation?.let { ThemeBounds.saturation.clamp(it) } ?: saturation,
    )
}

/** Sparse theme mutation; every field is optional. */
@Serializable
data class ThemePatch(
    val mood: Mood? = null,
    val accentHex: String? = null,
    val radiusScale: Float? = null,
    val elevationScale: Float? = null,
    val blurScale: Float? = null,
    val typeScale: Float? = null,
    val motionScale: Float? = null,
    val saturation: Float? = null,
)

@Serializable
enum class Mood {
    /** Default: deep indigo glass, cool highlights. */
    DUSK,
    /** Amber/terracotta tints, softer contrast. */
    WARM,
    /** Low saturation sage/stone, minimal motion. */
    CALM,
    VIVID,
    NOIR,
    DAYLIGHT,
}

object ThemeBounds {
    val radius = 0.5f..1.6f
    val elevation = 0f..2f
    val blur = 0f..2f
    /** Accessibility floor and layout ceiling for dynamic type. */
    val type = 0.85f..1.5f
    /** 0 = reduced motion (respects OS setting too). */
    val motion = 0f..1.5f
    val saturation = 0.2f..1.3f
}

private fun ClosedFloatingPointRange<Float>.clamp(v: Float): Float = v.coerceIn(start, endInclusive)

private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
fun isHexColor(value: String): Boolean = HEX.matches(value)
