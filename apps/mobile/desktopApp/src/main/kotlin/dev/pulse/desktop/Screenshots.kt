package dev.pulse.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.pulse.core.ai.IntentInput
import dev.pulse.core.ai.Modality
import dev.pulse.core.platform.PlatformContext
import dev.pulse.core.prefs.UserRole
import dev.pulse.ui.PulseApp
import dev.pulse.ui.PulseGraph
import dev.pulse.ui.Screen
import java.io.File
import kotlinx.coroutines.runBlocking
import javax.imageio.ImageIO

/**
 * Headless renderer for docs and visual-regression baselines. Every screen is the real shared
 * UI driven through the real intent pipeline (rules rung) with real utterances. Nothing is mocked.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "screenshots").apply { mkdirs() }
    val widthDp = 412
    val heightDp = 892
    val density = 2.5f

    fun shoot(name: String, startScreen: Screen, heightOverrideDp: Int = heightDp, firstLaunch: Boolean = false, setup: suspend PulseGraph.() -> Unit = {}) {
        val graph = PulseGraph.create(PlatformContext(), launcher = { }, useSampleData = true)
        if (!firstLaunch) {
            // Past the one-time account setup.
            graph.roles.setRole(UserRole.REGULAR)
            graph.profiles.save(dev.pulse.core.prefs.UserProfile("Sanjay"))
        }
        runBlocking { graph.setup() }
        ImageComposeScene((widthDp * density).toInt(), (heightOverrideDp * density).toInt(), Density(density)) {
            PulseApp(graph, startScreen)
        }.use { scene ->
            // Advance the frame clock so theme cross-fades, list animations and solid rotation settle.
            var t = 0L
            repeat(150) { scene.render(t); t += 16_000_000L }
            val image = scene.render(t + 2_400_000_000L)
            ImageIO.write(image.toComposeImageBitmap().toAwtImage(), "png", File(out, "$name.png"))
            println("wrote ${File(out, "$name.png").path}")
        }
    }

    suspend fun PulseGraph.say(text: String, vararg tags: String) =
        pipeline.submit(IntentInput(text, if (tags.isEmpty()) setOf(Modality.TEXT) else setOf(Modality.CAMERA, Modality.VOICE), tags.toList()))

    shoot("01-landing", Screen.Landing)
    shoot("02-new-cafes-koramangala", Screen.Discover) { say("Show me new cafes in Koramangala within 3 km") }
    shoot("03-theatre-this-weekend", Screen.Discover, heightOverrideDp = 1_500) { say("Any drama or theatre near Ravindra Kalakshetra this weekend?") }
    shoot("04-warm-quieter-theme", Screen.Discover) {
        say("Any drama or theatre near Ravindra Kalakshetra this weekend?")
        say("Change my app theme to something warm and quieter.")
    }
    shoot("05-map-moved-to-top", Screen.Discover) {
        say("Show me new cafes in Koramangala within 3 km")
        say("Move the map to the top of the screen.")
    }
    shoot("06-camera-voice-dosa", Screen.Discover) { say("Where can I get this near me, something cheap?", "dosa", "plate") }
    shoot("07-landing-warm-theme", Screen.Landing) { say("Change my app theme to something warm and quieter.") }
    shoot("08-unclear-intent-suggestions", Screen.Discover) { say("hmm") }
    shoot("09-first-launch-role", Screen.Landing, firstLaunch = true)
    shoot("10-vague-request-category-picker", Screen.Discover) { say("show me something nearby") }
    shoot("11-fashion-across-categories", Screen.Discover) { say("fashion near Koramangala") }
}
