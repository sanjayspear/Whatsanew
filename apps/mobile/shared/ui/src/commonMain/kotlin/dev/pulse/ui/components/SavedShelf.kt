package dev.pulse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pulse.core.data.ResolvedItem
import dev.pulse.core.data.haversineMeters
import dev.pulse.dsl.Node
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope

/** `shelf.saved`: the user's saved places, newest first, as cards with their actions. */
object SavedShelfComponent : PulseComponent {
    override val key = "shelf.saved"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val saved = remember(scope.dataVersion) { scope.session.saved() }
        val here = scope.anchorOf(null)
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText("SAVED PLACES · ${saved.size}", style = PulseType.overline.copy(color = Pulse.colors.accent), modifier = Modifier.padding(start = 4.dp))
            if (saved.isEmpty()) {
                BasicText(
                    "Nothing saved yet. Tap the bookmark on any place to keep it here, even offline.",
                    style = PulseType.body,
                    modifier = Modifier.fillMaxWidth().glass().padding(20.dp),
                )
            }
            saved.forEach { item ->
                val r = ResolvedItem(item, haversineMeters(here.lat, here.lng, item.lat, item.lng))
                GlassCard(
                    item = r,
                    languageTag = scope.languageTag,
                    nowMs = scope.nowMs(),
                    onOpen = {},
                    actions = { ActionSheet(node.actions, r, { scope.dispatch(it, r) }, isActive = { scope.withItem(r).isActive(it) }) },
                )
            }
        }
    }
}
