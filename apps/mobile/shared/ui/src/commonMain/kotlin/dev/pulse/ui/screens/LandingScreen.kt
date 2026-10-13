package dev.pulse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.pulse.ui.components.AvatarBadge
import dev.pulse.ui.components.BrandMark
import dev.pulse.ui.components.GeometricRotatingButton
import dev.pulse.ui.components.NeuButton
import dev.pulse.ui.design.PulseBackground
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType

/** The only fixed screen in the app (by design): the tagline and the two doors. */
@Composable
fun LandingScreen(
    onSearchAround: () -> Unit,
    onRecentlyLaunched: () -> Unit,
    onMenu: () -> Unit = {},
    onProfile: () -> Unit = {},
    avatarId: String? = null,
    photo: ByteArray? = null,
    name: String? = null,
) {
    PulseBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuButton(PulseIcons.Menu, null, onMenu, size = 40, description = "Open menu", modifier = Modifier.testTag("menu-button"))
                BrandMark(26, Modifier.padding(start = 14.dp))
                BasicText("Search Around", style = PulseType.title.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.01.em), modifier = Modifier.padding(start = 10.dp))
                Spacer(Modifier.weight(1f))
                AvatarBadge(avatarId, photo, 40, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onProfile).testTag("landing-profile"), name = name)
            }
            BasicText(
                "One Search.\nEndless Possibilities.",
                style = PulseType.display.copy(fontSize = PulseType.display.fontSize * 0.82f),
                modifier = Modifier.padding(top = 28.dp).testTag("tagline"),
            )
            BasicText(
                "Temples to tech parks, salons to shows: speak, type or ask in your own words.",
                style = PulseType.body,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                GeometricRotatingButton("Search Around", "Places, services, events and more near you", onSearchAround, Modifier.fillMaxWidth().weight(1f).testTag("door-search-around"))
                GeometricRotatingButton("Search Recently Launched", "New openings, launches and upcoming events", onRecentlyLaunched, Modifier.fillMaxWidth().weight(1f).testTag("door-recently-launched"), phaseDeg = 60f)
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
