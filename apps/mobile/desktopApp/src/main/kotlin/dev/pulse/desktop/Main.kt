package dev.pulse.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.pulse.core.platform.PlatformContext
import dev.pulse.ui.PulseApp
import dev.pulse.ui.PulseGraph

/** Phone-sized dev window: the whole shared app with the rules-only AI ladder. */
fun main() = application {
    val graph = PulseGraph.create(PlatformContext())
    Window(onCloseRequest = ::exitApplication, title = "Pulse (desktop harness)", state = rememberWindowState(size = DpSize(412.dp, 900.dp))) {
        PulseApp(graph)
    }
}
