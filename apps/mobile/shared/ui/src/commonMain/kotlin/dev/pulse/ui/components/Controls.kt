package dev.pulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pulse.core.prefs.Avatars
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseTextField
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.design.neumorphic
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Shared building blocks for the app's own pages (profile, settings, merchant, booking). */

/** Page title block: overline + large title + optional explanation. */
@Composable
fun PageHeader(overline: String, title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        BasicText(overline.uppercase(), style = PulseType.overline.copy(color = Pulse.colors.accent))
        BasicText(title, style = PulseType.display, modifier = Modifier.padding(top = 6.dp))
        body?.let { BasicText(it, style = PulseType.body, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** A frosted card with a small heading. */
@Composable
fun Section(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        title?.let { BasicText(it.uppercase(), style = PulseType.overline.copy(color = Pulse.colors.accent)) }
        content()
    }
}

@Composable
fun PulseChip(label: String, tag: String, selected: Boolean = false, onClick: () -> Unit) {
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

/** Labelled input with an optional error line. Values are kept by the caller. */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    helper: String? = null,
) {
    val c = Pulse.colors
    Column(modifier.fillMaxWidth()) {
        BasicText(label, style = PulseType.label.copy(color = c.textPrimary))
        PulseTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            onSubmit = {},
            testTag = tag,
            singleLine = singleLine,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            keyboardType = keyboardType,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
                .glass(RoundedCornerShape(16.dp), strength = 1.6f)
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        )
        when {
            error != null -> BasicText(error, style = PulseType.label.copy(color = ErrorColor), modifier = Modifier.padding(top = 4.dp))
            helper != null -> BasicText(helper, style = PulseType.label, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

internal val ErrorColor = Color(0xFFFF8A80)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Pulse.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .neumorphic(RoundedCornerShape(50))
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = PulseType.cardTitle.copy(color = if (enabled) c.accent else c.textSecondary))
    }
}

/** Low-emphasis text button ("Remove", "Clear all"). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, color: Color? = null) {
    BasicText(
        text,
        style = PulseType.label.copy(color = color ?: Pulse.colors.accent, fontWeight = FontWeight.SemiBold),
        modifier = modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClick).testTag(tag).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** One tappable row: icon, title, optional subtitle, optional trailing text. */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    tag: String,
    subtitle: String? = null,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val c = Pulse.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).testTag(tag).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(icon, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(c.accent))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            BasicText(title, style = PulseType.cardTitle)
            subtitle?.let { BasicText(it, style = PulseType.label, modifier = Modifier.padding(top = 2.dp)) }
        }
        trailing?.let { BasicText(it, style = PulseType.label.copy(color = c.accent)) }
    }
}

/** On/off switch row drawn with the neumorphic language (no Material dependency). */
@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, tag: String, onToggle: (Boolean) -> Unit) {
    val c = Pulse.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Switch) { onToggle(!checked) }.testTag(tag).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = PulseType.cardTitle)
            subtitle?.let { BasicText(it, style = PulseType.label, modifier = Modifier.padding(top = 2.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(52.dp).height(30.dp).neumorphic(RoundedCornerShape(50), pressed = checked).padding(4.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(22.dp).clip(CircleShape).then(Modifier.glass(CircleShape, strength = if (checked) 3f else 1.2f))) {
                if (checked) Canvas(Modifier.fillMaxSize()) { drawCircle(c.accent) }
            }
        }
    }
}

/** Avatar: the user's photo if they added one, else a built-in glyph on a colour. */
@Composable
fun AvatarBadge(avatarId: String?, photo: ByteArray?, size: Int, modifier: Modifier = Modifier, name: String? = null) {
    val bitmap = rememberBitmap(photo)
    val index = Avatars.ids.indexOf(avatarId).coerceAtLeast(0)
    val colors = listOf(Color(0xFF7C9CFF), Color(0xFFFFA26B), Color(0xFF5FD3A3), Color(0xFF5BC0EB), Color(0xFFE58FD8), Color(0xFFB59CFF))
    Box(modifier.size(size.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Canvas(Modifier.fillMaxSize()) { drawCircle(colors[index % colors.size]) }
            val glyph = listOf("✦", "☀", "❦", "≈", "★", "☾")[index % 6]
            BasicText(glyph, style = PulseType.title.copy(color = Color.White, fontSize = (size * 0.42f).toInt().let { androidx.compose.ui.unit.TextUnit(it.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp) }))
        }
    }
}

/** Decodes small image bytes once per value. Corrupt images render as nothing. */
@Composable
fun rememberBitmap(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
}

/** Closes a page from inside its content (pages also have the host's back button). */
@Composable
fun CloseRow(onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        NeuButton(PulseIcons.Close, null, onClose, size = 40, description = "Close")
    }
}
