package dev.pulse.intent.domain

import dev.pulse.dsl.GeoAnchor
import dev.pulse.dsl.GeoFilter
import dev.pulse.dsl.IntentFrame
import dev.pulse.dsl.LayoutPrefs
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.Task
import dev.pulse.dsl.UiOp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class IntentServiceTest {
    private val cafes = IntentFrame(Task.DISCOVER, ListingQuery(listOf("food.cafe"), near = GeoFilter(GeoAnchor.Place("Koramangala"), 3_000)))

    @Test
    fun `server composes the same surface structure as the device`() {
        val patch = ResolveIntent({ _, _ -> cafes }, { true }).resolve("new cafes in koramangala", "en-IN", LayoutPrefs())
        val surface = (patch.ops.single() as UiOp.ReplaceSurface).surface
        assertThat(surface.root.children.map { it.role }).containsExactly("header", "map", "results")
    }

    @Test @Disabled("P4: golden parity over packages/ui-dsl/examples against ai-server")
    fun `on-device and server frames agree on the golden set`() = Unit
}
