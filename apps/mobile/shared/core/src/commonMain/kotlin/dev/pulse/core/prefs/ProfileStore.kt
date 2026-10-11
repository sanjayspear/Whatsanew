package dev.pulse.core.prefs

import dev.pulse.dsl.LayoutPrefs
import dev.pulse.dsl.ThemeSpec

/** User-level preferences produced by PROFILE-persisted patches ("make it warm", "map on top"). */
interface ProfileStore {
    fun theme(): ThemeSpec
    fun saveTheme(theme: ThemeSpec)
    fun layout(): LayoutPrefs
    fun saveLayout(prefs: LayoutPrefs)
}

class InMemoryProfileStore(
    private var theme: ThemeSpec = ThemeSpec(),
    private var layout: LayoutPrefs = LayoutPrefs(),
) : ProfileStore {
    override fun theme() = theme
    override fun saveTheme(theme: ThemeSpec) { this.theme = theme }
    override fun layout() = layout
    override fun saveLayout(prefs: LayoutPrefs) { layout = prefs }
}
