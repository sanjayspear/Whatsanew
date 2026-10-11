package dev.pulse.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
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
    PulseBackground {
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
    fun onboarding() = Surface(
        id = "onboarding",
        root = Node("root", "layout.stack", role = Roles.ROOT, children = listOf(
            Node("role", "onboarding.role_picker", props = JsonObject(mapOf("mode" to JsonPrimitive("onboarding")))),
        )),
    )

    fun settings() = Surface(
        id = "settings",
        root = Node("root", "layout.stack", role = Roles.ROOT, children = listOf(
            Node("role", "onboarding.role_picker", props = JsonObject(mapOf("mode" to JsonPrimitive("settings")))),
            Node(
                "saved", "shelf.saved",
                actions = listOf(
                    ActionSpec(ActionKind.NAVIGATE, mapOf("lat" to "\$item.lat", "lng" to "\$item.lng")),
                    ActionSpec(ActionKind.SAVE, mapOf("listingId" to "\$item.id")),
                    ActionSpec(ActionKind.SHARE, mapOf("listingId" to "\$item.id")),
                ),
            ),
        )),
    )
}
