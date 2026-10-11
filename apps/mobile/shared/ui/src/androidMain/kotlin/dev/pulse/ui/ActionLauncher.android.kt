package dev.pulse.ui

import android.content.Intent
import android.net.Uri
import dev.pulse.core.platform.PlatformContext

actual fun platformActionLauncher(context: PlatformContext): ActionLauncher = ActionLauncher { uri ->
    if (uri.startsWith("pulse://")) return@ActionLauncher // in-app routes handled by the host
    val action = if (uri.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW
    runCatching { context.android.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
