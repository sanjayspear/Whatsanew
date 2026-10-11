package dev.pulse.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.pulse.ui.components.GeometricRotatingButton
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseBackground
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType

/** The only fixed screen in the app (by design): the two-door entry. */
@Composable
fun LandingScreen(onWhatsNew: () -> Unit, onLegacy: () -> Unit) {
    PulseBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(PulseIcons.Spark, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Pulse.colors.accent))
                BasicText("pulse", style = PulseType.title.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.04.em), modifier = Modifier.padding(start = 8.dp))
            }
            BasicText("What's happening\naround you", style = PulseType.display, modifier = Modifier.padding(top = 28.dp))
            BasicText("Bengaluru · speak, type or point your camera", style = PulseType.body, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                GeometricRotatingButton("What's New", "Fresh openings, tonight's shows", onWhatsNew, Modifier.fillMaxWidth().weight(1f))
                GeometricRotatingButton("Legacy", "Browse every category, classic style", onLegacy, Modifier.fillMaxWidth().weight(1f), phaseDeg = 60f)
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
