package dev.pulse.ui

import androidx.compose.ui.window.ComposeUIViewController
import dev.pulse.core.platform.PlatformContext
import platform.UIKit.UIViewController

/** Called from Swift: `MainViewControllerKt.MainViewController(context: PlatformContext(llm:speech:crypto:))`. */
fun MainViewController(context: PlatformContext): UIViewController {
    val graph = PulseGraph.create(context)
    return ComposeUIViewController { PulseApp(graph) }
}
