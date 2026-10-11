package dev.pulse.ui

import dev.pulse.core.data.ResolvedItem
import dev.pulse.core.platform.PlatformContext
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.ActionSpec

/** Opens OS handlers (dialer, maps, YouTube) and the system share sheet. One small actual per platform. */
fun interface ActionLauncher {
    fun open(uri: String)

    /** Native share sheet (Android chooser / iOS UIActivityViewController). No-op where unsupported. */
    fun share(subject: String, text: String) {}
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
        // Save and Share are handled in-app by PulseGraph.dispatch (shelf + share sheet), not as URIs.
        ActionKind.SAVE, ActionKind.SHARE -> null
        ActionKind.REFINE -> null
    }
}

/** Text for the share sheet: name, address and an open map link (OpenStreetMap). */
fun shareText(item: ResolvedItem): Pair<String, String> {
    val l = item.item
    val name = l.title("en")
    val map = "https://www.openstreetmap.org/?mlat=${l.lat}&mlon=${l.lng}#map=18/${l.lat}/${l.lng}"
    val body = listOfNotNull(name, l.address, l.phone, map).joinToString("\n")
    return name to "$body\n\nShared from Pulse"
}
