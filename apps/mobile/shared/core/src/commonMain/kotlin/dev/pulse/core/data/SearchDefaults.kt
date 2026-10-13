package dev.pulse.core.data

import dev.pulse.dsl.GeoFilter

/**
 * The user's default search radius (Settings → Default search radius, 6 km out of the box).
 *
 * Process-wide on purpose: the rule parser runs inside each platform's AI ladder and the pipeline fills
 * unanchored searches, and both must agree with the setting without threading it through every engine.
 * Only [dev.pulse.core.prefs.AppSettingsStore] writes it.
 */
object SearchDefaults {
    var radiusMeters: Int = GeoFilter.DEFAULT_RADIUS_METERS
        set(value) { field = value.coerceIn(GeoFilter.MIN_RADIUS_METERS, GeoFilter.MAX_RADIUS_METERS) }

    /** Choices offered in Settings and in the "nothing nearby" prompt. */
    val radiusChoicesKm = listOf(2, 4, 6, 10, 15, 25)
}
