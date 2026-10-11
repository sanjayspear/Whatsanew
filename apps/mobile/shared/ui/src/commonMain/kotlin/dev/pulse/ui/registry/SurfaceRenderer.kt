package dev.pulse.ui.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.pulse.dsl.Surface
import dev.pulse.ui.design.PulseTheme

/**
 * The DSL renderer: `Surface -> pixels`. It knows nothing about cafes, maps or videos. It
 * applies the surface theme and hands the root node to the registry. Layout animation (reorder,
 * insert, remove) is handled by keyed lazy items in the layout components.
 */
@Composable
fun SurfaceRenderer(surface: Surface, scope: RenderScope, modifier: Modifier = Modifier) {
    PulseTheme(surface.theme) {
        scope.RenderNode(surface.root, modifier)
    }
}
