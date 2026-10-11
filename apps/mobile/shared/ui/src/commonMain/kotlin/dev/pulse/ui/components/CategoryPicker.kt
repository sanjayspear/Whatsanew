package dev.pulse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.pulse.core.data.CategoryChoice
import dev.pulse.core.data.CategoryChoices
import dev.pulse.core.signals.Signal
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.Node
import dev.pulse.dsl.PulseJson
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseTextField
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.design.neumorphic
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope

/**
 * `intent.category_picker`: the specificity fallback. The composer places it on any surface whose
 * request names no category, so the AI layer triggers it simply by being unsure.
 *
 * A choice repaints the surface with that category (keeping location and radius) and is logged
 * as a personalization signal. "Other" opens the shared keyboard-safe [PulseTextField]; its text
 * goes through the full intent pipeline (rules, then the on-device model).
 */
object CategoryPickerComponent : PulseComponent {
    override val key = "intent.category_picker"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val base = remember(node.props) {
            node.props["query"]?.let { PulseJson.decodeFromJsonElement(ListingQuery.serializer(), it) } ?: ListingQuery()
        }
        CategoryPicker(
            onChoice = { choice ->
                choice.categories.forEach { scope.signal(Signal(Signal.Kind.REFINE, it, atEpochMs = scope.nowMs())) }
                scope.session.discover(base.copy(categories = choice.categories), choice.label)
            },
            onOther = { text ->
                scope.signal(Signal(Signal.Kind.REFINE, "other", atEpochMs = scope.nowMs()))
                scope.submitIntent(text)
            },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    onChoice: (CategoryChoice) -> Unit,
    onOther: (String) -> Unit,
    modifier: Modifier = Modifier,
    choices: List<CategoryChoice> = CategoryChoices.All,
) {
    val c = Pulse.colors
    var otherOpen by rememberSaveable { mutableStateOf(false) }
    var otherText by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxWidth().glass().padding(18.dp)) {
        BasicText("WHAT ARE YOU IN THE MOOD FOR?", style = PulseType.overline.copy(color = c.accent))
        BasicText("Pick a category and I'll narrow it down.", style = PulseType.body, modifier = Modifier.padding(top = 4.dp))
        FlowRow(
            Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            choices.forEach { choice -> Chip(choice.label, "category-${choice.id}") { onChoice(choice) } }
            Chip("Other", "category-other", selected = otherOpen) { otherOpen = !otherOpen }
        }
        if (otherOpen) {
            PulseTextField(
                value = otherText,
                onValueChange = { otherText = it },
                placeholder = "Type a category, e.g. pottery, gaming cafés",
                onSubmit = onOther,
                testTag = "category-other-input",
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth()
                    .glass(RoundedCornerShape(18.dp), strength = 1.6f).padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun Chip(label: String, tag: String, selected: Boolean = false, onClick: () -> Unit) {
    val c = Pulse.colors
    BasicText(
        label,
        style = PulseType.label.copy(color = if (selected) c.accent else c.textPrimary),
        modifier = Modifier
            .height(40.dp)
            .neumorphic(RoundedCornerShape(50), pressed = selected)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}
