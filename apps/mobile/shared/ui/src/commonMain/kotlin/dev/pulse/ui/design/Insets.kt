package dev.pulse.ui.design

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Bottom insets that keep an element above *both* the navigation bar and the on-screen keyboard.
 *
 * Why the old code failed: the app is edge-to-edge (`enableEdgeToEdge`), and in that mode
 * Android no longer resizes the window for the keyboard (`adjustResize` is ignored), so the app must
 * apply `WindowInsets.ime` itself. The intent bar only applied `navigationBarsPadding()`, so
 * the keyboard drew straight over it. `union` takes the larger of the two per side, so there's no
 * double padding when the keyboard (which already covers the nav bar) is open. Compose Multiplatform
 * reports `WindowInsets.ime` on iOS too, so no expect/actual is needed.
 */
val WindowInsets.Companion.keyboardOrNavBar: WindowInsets
    @Composable get() = WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)

@Composable
fun Modifier.keyboardSafe(): Modifier = windowInsetsPadding(WindowInsets.keyboardOrNavBar)
