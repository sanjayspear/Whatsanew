package dev.pulse.ui

import dev.pulse.core.platform.PlatformContext
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun platformActionLauncher(context: PlatformContext): ActionLauncher = object : ActionLauncher {
    override fun open(uri: String) {
        NSURL.URLWithString(uri)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
    }

    override fun share(subject: String, text: String) {
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(sheet, animated = true, completion = null)
    }
}
