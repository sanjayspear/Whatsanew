package dev.pulse.ui

import dev.pulse.core.platform.PlatformContext
import java.awt.Desktop
import java.net.URI

actual fun platformActionLauncher(context: PlatformContext): ActionLauncher = ActionLauncher { uri ->
    if (uri.startsWith("http") && Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().browse(URI(uri)) }
    else println("[pulse] action: $uri")
}
