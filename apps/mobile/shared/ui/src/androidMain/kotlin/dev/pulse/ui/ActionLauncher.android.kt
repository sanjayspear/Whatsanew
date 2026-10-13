package dev.pulse.ui

import android.content.Intent
import android.net.Uri
import dev.pulse.core.platform.PlatformContext

actual fun platformActionLauncher(context: PlatformContext): ActionLauncher = object : ActionLauncher {
    override fun open(uri: String) {
        if (uri.startsWith("pulse://")) return // in-app routes are handled by the host
        val action = if (uri.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW
        runCatching { context.android.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun isInstalled(androidPackage: String): Boolean = runCatching {
        context.android.packageManager.getPackageInfo(androidPackage, 0)
        true
    }.getOrDefault(false)

    override fun share(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        runCatching {
            context.android.startActivity(Intent.createChooser(send, "Share $subject").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
