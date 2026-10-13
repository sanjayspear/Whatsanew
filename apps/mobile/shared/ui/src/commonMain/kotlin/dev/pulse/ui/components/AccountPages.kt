package dev.pulse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pulse.core.data.SearchDefaults
import dev.pulse.dsl.Mood
import dev.pulse.dsl.Node
import dev.pulse.dsl.ThemePatch
import dev.pulse.ui.Screen
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** `page.header`: title block for app pages (`overline`, `title`, `body` props). */
object PageHeaderComponent : PulseComponent {
    override val key = "page.header"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        fun prop(k: String) = node.props[k]?.jsonPrimitive?.contentOrNull
        PageHeader(prop("overline").orEmpty(), prop("title").orEmpty(), prop("body"), modifier)
    }
}

/** `history.list`: recent searches. Tap to search again, remove one, or clear all. */
object HistoryListComponent : PulseComponent {
    override val key = "history.list"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val session = scope.session
        val entries = remember(scope.dataVersion) { session.history() }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(null) {
                ToggleRow(
                    "Keep search history", "Stored on this phone only. Turning it off also deletes it.",
                    session.historyEnabled, "history-toggle",
                ) { session.setHistoryEnabled(it) }
            }
            if (entries.isEmpty()) {
                BasicText(
                    if (session.historyEnabled) "No searches yet. What you search for will appear here." else "Search history is off.",
                    style = PulseType.body,
                    modifier = Modifier.fillMaxWidth().glass().padding(20.dp),
                )
            } else {
                Section("Recent searches · ${entries.size}") {
                    entries.forEachIndexed { i, e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                ActionRow(PulseIcons.Clock, e.text, "history-item-$i", subtitle = Format.ago(e.atEpochMs, scope.nowMs())) {
                                    session.navigate(Screen.Discover)
                                    scope.submitIntent(e.text)
                                }
                            }
                            NeuButton(PulseIcons.Close, null, { session.removeHistory(e.text) }, size = 34, description = "Remove ${e.text}")
                        }
                    }
                    TextAction("Clear all history", { session.clearHistory(); session.toast("Search history cleared") }, "history-clear")
                }
            }
        }
    }
}

/** `settings.panel`: only settings whose feature exists today. */
object SettingsPanelComponent : PulseComponent {
    override val key = "settings.panel"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val session = scope.session
        val settings = remember(scope.dataVersion) { session.settings() }
        val theme = session.theme
        var area by rememberSaveable { mutableStateOf(settings.manualArea?.name.orEmpty()) }
        var areaError by remember { mutableStateOf<String?>(null) }
        var confirmDelete by remember { mutableStateOf(false) }

        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Location") {
                BasicText(session.locationStatus, style = PulseType.body)
                if (!session.locationAllowed) TextAction("Allow location", { session.requestLocation() }, "settings-location-allow")
                FormField(
                    "Search from a chosen area instead", area, { area = it; areaError = null }, "e.g. Whitefield, Jayanagar", "settings-area",
                    error = areaError,
                    helper = settings.manualArea?.let { "Searching around ${it.name}. Clear the field and save to use your location again." }
                        ?: "Useful if location is off, or to search somewhere you aren't.",
                )
                TextAction(if (area.isBlank()) "Use my location" else "Save area", {
                    if (session.setManualArea(area.ifBlank { null })) {
                        session.toast(if (area.isBlank()) "Using your location" else "Searching around ${session.settings().manualArea?.name}")
                        area = session.settings().manualArea?.name.orEmpty()
                    } else areaError = "We don't know “${area.trim()}” yet. Try a Bengaluru neighbourhood name."
                }, "settings-area-save")
            }
            Section("Default search radius") {
                BasicText("Searches without a distance look this far around you.", style = PulseType.body)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchDefaults.radiusChoicesKm.forEach { km ->
                        PulseChip("$km km", "radius-$km", selected = settings.defaultRadiusKm == km) { session.setDefaultRadiusKm(km) }
                    }
                }
            }
            Section("Appearance") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Mood.DUSK to "Dark", Mood.DAYLIGHT to "Light", Mood.NOIR to "Black", Mood.WARM to "Warm").forEach { (mood, label) ->
                        PulseChip(label, "theme-${mood.name.lowercase()}", selected = theme.mood == mood) { session.setTheme(ThemePatch(mood = mood)) }
                    }
                }
            }
            Section("Accessibility") {
                BasicText("Text size", style = PulseType.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(1f to "Standard", 1.15f to "Large", 1.3f to "Larger").forEach { (scale, label) ->
                        PulseChip(label, "text-$label", selected = kotlin.math.abs(theme.typeScale - scale) < 0.01f) { session.setTheme(ThemePatch(typeScale = scale)) }
                    }
                }
                ToggleRow("Reduce motion", "Calmer animations", theme.motionScale < 0.99f, "reduce-motion") { on ->
                    session.setTheme(ThemePatch(motionScale = if (on) 0.4f else 1f))
                }
            }
            Section("Privacy") {
                ToggleRow("Keep search history", "On this phone only", session.historyEnabled, "settings-history") { session.setHistoryEnabled(it) }
                ActionRow(PulseIcons.Clock, "Manage search history", "settings-history-open") { session.navigate(Screen.History) }
                ActionRow(PulseIcons.Shield, "Privacy Policy and Terms", "settings-legal") { session.navigate(Screen.Legal) }
            }
            Section("Account") {
                ActionRow(PulseIcons.User, "Edit profile", "settings-profile") { session.navigate(Screen.Profile) }
                ActionRow(PulseIcons.Logout, "Sign out", "settings-signout", subtitle = "Forget your profile on this phone; saved places stay") { session.signOut() }
                ActionRow(
                    PulseIcons.Trash,
                    if (confirmDelete) "Tap again to delete everything" else "Delete my data",
                    "settings-delete",
                    subtitle = "Profile, history, saved places and posts on this phone",
                ) {
                    if (confirmDelete) session.deleteAllData() else confirmDelete = true
                }
            }
            Section("App") {
                BasicText("Search Around ${session.appVersion}", style = PulseType.cardTitle)
                BasicText("Updates arrive through your app store. Offline Bengaluru map data: © OpenStreetMap contributors.", style = PulseType.label)
                ActionRow(PulseIcons.Info, "About", "settings-about") { session.navigate(Screen.About) }
            }
        }
    }
}

/** `info.page`: About, Help & Support, Privacy & Terms (`page` prop). Plain language, on-device facts only. */
object InfoPageComponent : PulseComponent {
    override val key = "info.page"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        when (node.props["page"]?.jsonPrimitive?.contentOrNull) {
            "about" -> About(scope, modifier)
            "help" -> Help(scope, modifier)
            "legal" -> Legal(modifier)
        }
    }

    @Composable
    private fun About(scope: RenderScope, modifier: Modifier) {
        val session = scope.session
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(56)
                    Column(Modifier.padding(start = 14.dp)) {
                        BasicText("Search Around", style = PulseType.title)
                        BasicText("Version ${session.appVersion}", style = PulseType.label)
                    }
                }
                BasicText("One Search. Endless Possibilities.", style = PulseType.cardTitle.copy(color = Pulse.colors.accent))
                BasicText(
                    "One place to find almost anything around you: temples and churches, salons and juice shops, IT parks, cinemas, plays and events, newly opened shops and new services.",
                    style = PulseType.body,
                )
            }
            Section("What it does") {
                listOf(
                    "Understands everyday requests (“fresh juice within 3 km”) with AI that runs on your phone.",
                    "Shows real places from OpenStreetMap, within 6 km by default, and asks before searching further.",
                    "Book Now hands you to BookMyShow, District or the organiser, in their app if you have it.",
                    "Search Recently Launched shows new openings and merchant announcements.",
                    "Works offline in Bengaluru.",
                ).forEach { BasicText("•  $it", style = PulseType.body) }
            }
            Section("Made by") {
                BasicText("Sanjay Y M, with Ashok Kumar S and Claude (Anthropic).", style = PulseType.body)
                BasicText("Place data © OpenStreetMap contributors (ODbL). On-device AI: Qwen2.5 (Apache-2.0).", style = PulseType.label)
                ActionRow(PulseIcons.Shield, "Privacy Policy and Terms of Service", "about-legal") { session.navigate(Screen.Legal) }
                ActionRow(PulseIcons.Help, "Help and Support", "about-help") { session.navigate(Screen.Help) }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Help(scope: RenderScope, modifier: Modifier) {
        val session = scope.session
        var kind by rememberSaveable { mutableStateOf("Something isn't working") }
        var details by rememberSaveable { mutableStateOf("") }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Common questions") {
                listOf(
                    "Why don't I see results?" to "Search looks 6 km around you by default. When nothing is close, you'll be asked whether to look further or pick an area. You can change the default in Settings.",
                    "Why is Book Now missing on a place?" to "It only appears when there is a real place to book: a ticketing platform for cinemas, plays and events, or the organiser's own link.",
                    "Can I search somewhere else?" to "Yes. Say “salons in Whitefield”, or set an area in Settings → Location.",
                    "Where is my data?" to "Your profile, history and saved places stay on this phone. See Privacy Policy.",
                ).forEach { (q, a) ->
                    Column {
                        BasicText(q, style = PulseType.cardTitle)
                        BasicText(a, style = PulseType.body, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Section("Report a problem") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Something isn't working", "Wrong or outdated place", "Broken booking link", "Inappropriate listing").forEach {
                        PulseChip(it, "report-${it.take(8)}", selected = kind == it) { kind = it }
                    }
                }
                FormField("What happened?", details, { details = it }, "Place name, what you searched, what went wrong", "report-details", singleLine = false)
                PrimaryButton("Send report", {
                    session.reportProblem(kind, details)
                    details = ""
                }, "report-send")
                BasicText("Opens your share sheet with the report, so you choose how to send it (email, WhatsApp…).", style = PulseType.label)
            }
        }
    }

    @Composable
    private fun Legal(modifier: Modifier) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Privacy Policy") {
                listOf(
                    "What stays on your phone" to "Your name, avatar or photo, optional phone number, account type, search history, saved places and merchant posts are stored only on this device. There is no Search Around account server yet.",
                    "Location" to "Used only after you allow it, and only to find places near you. Searches run on your phone against the offline map; outside Bengaluru the app asks OpenStreetMap servers for places around the area you search.",
                    "Name searches" to "When you search for a specific name (a company, venue or shop), the name and your approximate area are sent to Photon, an OpenStreetMap search service, to find places the offline map doesn't have.",
                    "Microphone" to "Used only while you hold the mic button, through your phone's speech recogniser.",
                    "AI" to "Requests are understood on your phone. Nothing you type or say is sent to an AI service.",
                    "Links you open" to "Booking platforms, maps and websites have their own privacy policies.",
                    "Your choices" to "Turn search history off, clear it, sign out, or delete all data in Settings at any time.",
                ).forEach { (h, b) -> LegalParagraph(h, b) }
            }
            Section("Terms of Service") {
                listOf(
                    "Information" to "Places come from OpenStreetMap contributors and merchants. Check opening hours and details with the place before you go.",
                    "Bookings" to "Tickets are sold by the platform you choose (BookMyShow, District, or the organiser). Search Around doesn't sell tickets or take payments.",
                    "Merchant posts" to "Merchants must post accurate, lawful offerings. Misleading, duplicate or inappropriate posts are rejected or removed.",
                    "Sponsored content" to "There is none today. If it is ever added, it will be clearly marked as sponsored.",
                ).forEach { (h, b) -> LegalParagraph(h, b) }
                BasicText("Draft for review. Last updated 14 Oct 2026.", style = PulseType.label)
            }
        }
    }

    @Composable
    private fun LegalParagraph(heading: String, body: String) {
        Column {
            BasicText(heading, style = PulseType.cardTitle)
            BasicText(body, style = PulseType.body, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
