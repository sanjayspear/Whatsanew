package dev.pulse.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.ActionSpec
import dev.pulse.dsl.Node
import dev.pulse.dsl.Roles
import dev.pulse.dsl.Surface
import dev.pulse.ui.components.NeuButton
import dev.pulse.ui.design.PulseBackground
import dev.pulse.ui.design.PulseToast
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.registry.RenderScope
import dev.pulse.ui.registry.SurfaceRenderer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Minimal chrome around an app-defined [Surface] (onboarding, settings). Content comes from the registry. */
@Composable
fun SurfaceHost(surface: Surface, scope: RenderScope, onBack: (() -> Unit)? = null, toast: String? = null) {
    val focus = LocalFocusManager.current
    PulseBackground(Modifier.pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }) {
        SurfaceRenderer(surface, scope)
        PulseToast(toast)
        if (onBack != null) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                NeuButton(PulseIcons.Back, null, onBack, size = 40)
            }
        }
    }
}

/** App surfaces are DSL too: built from registered components, never hardcoded layouts. */
object AppSurfaces {
    private fun page(id: String, overline: String, title: String, body: String?, vararg content: Node) = Surface(
        id = id,
        root = Node("root", "layout.stack", role = Roles.ROOT, children = listOf(
            Node("header", "page.header", props = props("overline" to overline, "title" to title, "body" to body)),
        ) + content),
    )

    private fun props(vararg pairs: Pair<String, String?>) =
        JsonObject(pairs.filter { it.second != null }.associate { (k, v) -> k to JsonPrimitive(v) })

    private val savedActions = listOf(
        ActionSpec(ActionKind.BOOK, mapOf("listingId" to "\$item.id", "bookable" to "\$item.bookable")),
        ActionSpec(ActionKind.NAVIGATE, mapOf("lat" to "\$item.lat", "lng" to "\$item.lng")),
        ActionSpec(ActionKind.SAVE, mapOf("listingId" to "\$item.id")),
        ActionSpec(ActionKind.SHARE, mapOf("listingId" to "\$item.id")),
    )

    fun onboarding() = page(
        "onboarding", "Welcome to Search Around", "One Search.\nEndless Possibilities.",
        "Let's set up your account. Only your name is required.",
        Node("profile", "profile.editor", props = props("mode" to "onboarding")),
    )

    fun profile() = page(
        "profile", "Profile", "Your profile", null,
        Node("profile", "profile.editor", props = props("mode" to "edit")),
        Node("role", "onboarding.role_picker", props = props("mode" to "settings")),
    )

    fun settings() = page("settings", "Settings", "Settings", null, Node("settings", "settings.panel"))

    fun saved() = page("saved", "Saved Items", "Saved places", null, Node("saved", "shelf.saved", actions = savedActions))

    fun history() = page("history", "Search History", "Recent searches", null, Node("history", "history.list"))

    fun help() = page("help", "Help and Support", "How can we help?", null, Node("help", "info.page", props = props("page" to "help")))

    fun about() = page("about", "About", "About Search Around", null, Node("about", "info.page", props = props("page" to "about")))

    fun legal() = page("legal", "Privacy and Terms", "Privacy Policy and Terms of Service", null, Node("legal", "info.page", props = props("page" to "legal")))

    fun booking() = page("booking", "Book Now", "Choose where to book", null, Node("booking", "booking.options"))

    fun publish() = page(
        "publish", "Merchant", "Publish an offering",
        "Announce a new event, movie release, shop, service or promotion. It appears in search and in Search Recently Launched.",
        Node("publish", "merchant.publish"),
    )

    fun myPosts() = page("posts", "Merchant", "My posts", null, Node("posts", "merchant.posts"))
}
