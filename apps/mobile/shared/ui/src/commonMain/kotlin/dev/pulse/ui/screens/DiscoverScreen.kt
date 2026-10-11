package dev.pulse.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.pulse.core.pipeline.SurfaceState
import dev.pulse.dsl.EmptyProps
import dev.pulse.dsl.Node
import dev.pulse.ui.components.IntentSuggestionsComponent
import dev.pulse.ui.components.NeuButton
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseBackground
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.registry.RenderScope
import dev.pulse.ui.registry.SurfaceRenderer

/**
 * Host for every AI-generated surface. It has no layout of its own beyond chrome: the back
 * button, an on-device latency pill and the floating multimodal intent bar. Everything in
 * between is the DSL renderer.
 */
@Composable
fun DiscoverScreen(
    state: SurfaceState,
    scope: RenderScope,
    onSubmit: (String) -> Unit,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onBack: () -> Unit,
    initialText: String = "",
    listening: Boolean = false,
    showDiagnostics: Boolean = true,
) {
    val haze = remember { HazeState() }
    PulseBackground {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            val surface = state.surface
            if (surface != null) {
                SurfaceRenderer(surface, scope)
            } else {
                Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).padding(top = 72.dp)) {
                    IntentSuggestionsComponent.Render(Node("suggestions", IntentSuggestionsComponent.key, props = EmptyProps), scope, Modifier)
                }
            }
        }
        // Fade behind the status bar and chrome so scrolled content never collides with them.
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Pulse.colors.backgroundTop, Pulse.colors.backgroundTop.copy(alpha = 0.85f), Color.Transparent)))
                .statusBarsPadding()
                .height(84.dp),
        )
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuButton(PulseIcons.Back, null, onBack, size = 40)
            Spacer(Modifier.weight(1f))
            val latency = state.lastLatencyMs
            if (showDiagnostics && latency != null) {
                val source = state.frameSource?.name?.lowercase()?.replace('_', ' ') ?: "rules"
                BasicText(
                    "on-device · $source · ${if (latency < 1) "<1" else "$latency"} ms",
                    style = PulseType.label.copy(color = Pulse.colors.accent),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Pulse.colors.backgroundTop.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        IntentBar(
            haze = haze,
            initialText = initialText,
            listening = listening,
            onSubmit = onSubmit,
            onMic = onMic,
            onCamera = onCamera,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }
}

@Composable
fun IntentBar(
    haze: HazeState,
    initialText: String,
    onSubmit: (String) -> Unit,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
) {
    val c = Pulse.colors
    var text by remember(initialText) { mutableStateOf(initialText) }
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .glass(shape, strength = 1.4f)
            .hazeEffect(haze, HazeStyle(backgroundColor = c.backgroundBottom, tint = HazeTint(c.backgroundTop.copy(alpha = 0.55f)), blurRadius = 24.dp, noiseFactor = 0.05f))
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(PulseIcons.Spark, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(c.accent))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) BasicText(if (listening) "Listening…" else "Ask for anything nearby…", style = PulseType.body.copy(color = if (listening) c.accent else c.textSecondary))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = PulseType.body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) onSubmit(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NeuButton(PulseIcons.Mic, if (listening) "Listening" else null, onMic, size = 42)
        Spacer(Modifier.width(10.dp))
        NeuButton(PulseIcons.Camera, null, onCamera, size = 42)
    }
}
