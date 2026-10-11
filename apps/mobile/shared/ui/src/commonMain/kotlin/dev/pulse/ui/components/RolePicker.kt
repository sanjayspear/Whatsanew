package dev.pulse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.pulse.core.prefs.UserRole
import dev.pulse.dsl.Node
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.design.neumorphic
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `onboarding.role_picker`: "Are you a Regular user, or a Merchant?". The same component serves
 * first launch (`mode: onboarding`) and Settings (`mode: settings`, shows the current choice).
 */
object RolePickerComponent : PulseComponent {
    override val key = "onboarding.role_picker"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val settings = node.props["mode"]?.jsonPrimitive?.contentOrNull == "settings"
        RolePicker(current = if (settings) scope.session.role else null, onSelect = scope.session::setRole, modifier = modifier)
    }
}

@Composable
fun RolePicker(current: UserRole?, onSelect: (UserRole) -> Unit, modifier: Modifier = Modifier) {
    val c = Pulse.colors
    Column(modifier.fillMaxWidth().glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText(if (current == null) "WELCOME TO PULSE" else "YOUR ROLE", style = PulseType.overline.copy(color = c.accent))
        BasicText("Are you a Regular user, or a Merchant?", style = PulseType.title)
        BasicText(
            "Regular users discover what's around them. Merchants list their places and events. You can change this later in Settings.",
            style = PulseType.body,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoleOption("Regular user", "Discover nearby", current == UserRole.REGULAR, "role-regular", Modifier.weight(1f)) { onSelect(UserRole.REGULAR) }
            RoleOption("Merchant", "List my business", current == UserRole.MERCHANT, "role-merchant", Modifier.weight(1f)) { onSelect(UserRole.MERCHANT) }
        }
    }
}

@Composable
private fun RoleOption(title: String, subtitle: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Pulse.colors
    Column(
        modifier
            .height(96.dp)
            .neumorphic(RoundedCornerShape(Pulse.tokens.radiusSmall), pressed = selected)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(title, style = PulseType.cardTitle.copy(color = if (selected) c.accent else c.textPrimary))
        BasicText(if (selected) "Selected" else subtitle, style = PulseType.label)
    }
}
