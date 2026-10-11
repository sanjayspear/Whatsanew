package dev.pulse.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pulse.core.data.ResolvedItem
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.ActionSpec
import dev.pulse.dsl.Node
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.neumorphic
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope

/**
 * Reference component #5: a neumorphic action row (book / navigate / call / save / share).
 * The first action gets a labelled pill and the rest are icon buttons. `$item.x` args are
 * resolved against the bound listing here, at the edge, so DSL templates never carry real data.
 */
@Composable
fun ActionSheet(
    actions: List<ActionSpec>,
    item: ResolvedItem?,
    onAction: (ActionSpec) -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
    isActive: (ActionSpec) -> Boolean = { false },
) {
    // Actions are ordered by importance in the template; overflow (e.g. Share) moves to long-press.
    val usable = actions.filter { a -> a.args.values.none { it.startsWith("\$item.") && resolveArg(it, item) == null } }.take(maxVisible)
    if (usable.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        usable.forEachIndexed { i, action ->
            val resolved = action.copy(args = action.args.mapValues { (_, v) -> resolveArg(v, item) ?: v })
            NeuButton(
                icon = iconFor(action.kind),
                label = if (i == 0) labelFor(action.kind) else null,
                onClick = { onAction(resolved) },
                active = isActive(resolved),
                description = descriptionFor(action.kind, isActive(resolved)),
            )
        }
    }
}

@Composable
fun NeuButton(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 38,
    active: Boolean = false,
    description: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = if (label == null) CircleShape else RoundedCornerShape(50)
    val colors = Pulse.colors
    Box(
        modifier
            .height(size.dp)
            .then(if (label == null) Modifier.width(size.dp) else Modifier)
            .neumorphic(shape, pressed || active) // active (e.g. saved) stays debossed, with an accent icon
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description ?: label ?: icon.name }
            .padding(horizontal = if (label == null) 0.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(icon, null, Modifier.size((size * 0.47).dp), colorFilter = ColorFilter.tint(if (label != null || active) colors.accent else colors.textPrimary))
            if (label != null) {
                Spacer(Modifier.width(6.dp))
                BasicText(label, style = PulseType.label.copy(color = colors.accent))
            }
        }
    }
}

internal fun resolveArg(value: String, item: ResolvedItem?): String? =
    if (value.startsWith("\$item.")) item?.item?.field(value.removePrefix("\$item.")) else value

private fun iconFor(kind: ActionKind) = when (kind) {
    ActionKind.CALL -> PulseIcons.Phone
    ActionKind.NAVIGATE -> PulseIcons.Navigate
    ActionKind.BOOK -> PulseIcons.Ticket
    ActionKind.SAVE -> PulseIcons.Bookmark
    ActionKind.SHARE -> PulseIcons.Share
    ActionKind.PLAY_VIDEO -> PulseIcons.Play
    ActionKind.OPEN_URL, ActionKind.REFINE -> PulseIcons.Spark
}

private fun descriptionFor(kind: ActionKind, active: Boolean) = when (kind) {
    ActionKind.SAVE -> if (active) "Remove from saved" else "Save"
    ActionKind.SHARE -> "Share"
    ActionKind.CALL -> "Call"
    ActionKind.NAVIGATE -> "Directions"
    else -> null
}

private fun labelFor(kind: ActionKind) = when (kind) {
    ActionKind.CALL -> "Call"
    ActionKind.NAVIGATE -> "Go"
    ActionKind.BOOK -> "Book"
    ActionKind.SAVE -> "Save"
    ActionKind.SHARE -> "Share"
    ActionKind.PLAY_VIDEO -> "Play"
    ActionKind.OPEN_URL -> "Open"
    ActionKind.REFINE -> "More"
}

/** DSL-addressable standalone sheet (`action.sheet`). */
object ActionSheetComponent : PulseComponent {
    override val key = "action.sheet"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        ActionSheet(node.actions, scope.item, { scope.dispatch(it, scope.item) }, modifier, isActive = { scope.isActive(it) })
    }
}

/** Saved state for the Save button; other actions have no on/off state. */
internal fun RenderScope.isActive(action: ActionSpec): Boolean =
    action.kind == ActionKind.SAVE && action.args["listingId"]?.let(session::isSaved) == true
