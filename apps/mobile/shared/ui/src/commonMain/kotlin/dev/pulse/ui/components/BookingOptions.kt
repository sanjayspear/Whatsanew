package dev.pulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pulse.core.data.booking.BookingOption
import dev.pulse.core.signals.Signal
import dev.pulse.dsl.Node
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.design.neumorphic
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope

/**
 * `booking.options`: the Book Now page. Shows the chosen listing (and what the user searched for,
 * so the context isn't lost) and one clearly named button per booking destination.
 */
object BookingOptionsComponent : PulseComponent {
    override val key = "booking.options"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val session = scope.session
        val target = remember(scope.dataVersion) { session.bookingTarget() }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (target == null) {
                BasicText("Nothing selected to book. Go back and tap Book Now on a result.", style = PulseType.body, modifier = Modifier.fillMaxWidth().glass().padding(20.dp))
                return@Column
            }
            val item = target.item
            val options = remember(item) { session.bookingOptions(item) }
            Section(null) {
                BasicText(Format.category(item.category, scope.languageTag).uppercase(), style = PulseType.overline.copy(color = Pulse.colors.accent))
                BasicText(item.title(scope.languageTag), style = PulseType.title)
                BasicText(
                    listOfNotNull(item.address, Format.distance(target.distanceMeters) + " away", Format.eventTime(item)).joinToString("  ·  "),
                    style = PulseType.body,
                )
                session.bookingContext?.let { BasicText("You searched for “$it”", style = PulseType.label) }
            }
            if (options.isEmpty()) {
                BasicText(
                    "There's no verified booking page for this place yet. You can still get directions or share it.",
                    style = PulseType.body,
                    modifier = Modifier.fillMaxWidth().glass().padding(20.dp),
                )
            } else {
                BasicText("CHOOSE WHERE TO BOOK", style = PulseType.overline, modifier = Modifier.padding(start = 4.dp))
                options.forEachIndexed { i, option ->
                    PlatformButton(option, installed = option.platform.androidPackage?.let(session::isInstalled) == true, tag = "booking-option-$i") {
                        scope.signal(Signal(Signal.Kind.ATTEND, item.category, item.id, scope.nowMs()))
                        session.openUrl(option.url)
                    }
                }
                BasicText(
                    "Tickets are sold by the platform you choose. Show times and seats are on their page.",
                    style = PulseType.label,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PlatformButton(option: BookingOption, installed: Boolean, tag: String, onClick: () -> Unit) {
    val c = Pulse.colors
    val brand = Color(option.platform.color.toInt())
    Row(
        Modifier
            .fillMaxWidth()
            .neumorphic(RoundedCornerShape(Pulse.tokens.radiusSmall))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Book on ${option.platform.name}" }
            .testTag(tag)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name + monogram badge in the platform's colour (no third-party logo files are bundled).
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawRect(brand) }
            BasicText(option.platform.monogram, style = PulseType.cardTitle.copy(color = Color.White, fontWeight = FontWeight.Bold))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            BasicText(option.platform.name, style = PulseType.cardTitle)
            BasicText(option.hint, style = PulseType.label, maxLines = 2)
            BasicText(
                when {
                    installed -> "Opens in the ${option.platform.name} app"
                    option.platform.androidPackage != null || option.platform.id == "district" -> "Opens in their app if installed, otherwise the website"
                    else -> option.url.removePrefix("https://").substringBefore('/')
                },
                style = PulseType.label.copy(color = c.accent),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Image(PulseIcons.External, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(c.accent))
    }
}
