package dev.pulse.ui

import dev.pulse.core.data.ResolvedItem
import dev.pulse.core.platform.PlatformContext
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.ActionSpec

/** Opens OS handlers (dialer, maps, YouTube, share sheet). One small actual per platform. */
fun interface ActionLauncher {
    fun open(uri: String)
}

expect fun platformActionLauncher(context: PlatformContext): ActionLauncher

/** DSL action -> platform-neutral URI. `pulse://` URIs are handled in-app (booking, saving). */
object ActionUris {
    fun of(action: ActionSpec, item: ResolvedItem?): String? = when (action.kind) {
        ActionKind.CALL -> action.args["phone"]?.let { "tel:$it" }
        ActionKind.NAVIGATE -> {
            val lat = action.args["lat"] ?: item?.item?.lat?.toString()
            val lng = action.args["lng"] ?: item?.item?.lng?.toString()
            if (lat != null && lng != null) "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng" else null
        }
        ActionKind.PLAY_VIDEO -> action.args["videoId"]?.let { "https://www.youtube.com/watch?v=$it" }
        ActionKind.OPEN_URL -> action.args["url"]
        ActionKind.BOOK -> action.args["listingId"]?.let { "pulse://book/$it" }
        ActionKind.SAVE -> action.args["listingId"]?.let { "pulse://save/$it" }
        ActionKind.SHARE -> action.args["listingId"]?.let { "pulse://share/$it" }
        ActionKind.REFINE -> null
    }
}
