package dev.pulse.ui

import dev.pulse.core.platform.PlatformContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun platformActionLauncher(context: PlatformContext): ActionLauncher = ActionLauncher { uri ->
    NSURL.URLWithString(uri)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
}
