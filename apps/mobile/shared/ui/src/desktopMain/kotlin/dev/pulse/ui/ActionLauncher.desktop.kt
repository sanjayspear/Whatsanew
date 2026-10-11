package dev.pulse.ui

import dev.pulse.core.platform.PlatformContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

actual fun platformActionLauncher(context: PlatformContext): ActionLauncher = object : ActionLauncher {
    override fun open(uri: String) {
        if (uri.startsWith("http") && Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().browse(URI(uri)) }
        else println("[pulse] action: $uri")
    }

    /** Desktop has no share sheet: copy to the clipboard instead. */
    override fun share(subject: String, text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }
}
