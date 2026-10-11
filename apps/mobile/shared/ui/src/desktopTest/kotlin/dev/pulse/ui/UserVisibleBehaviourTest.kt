package dev.pulse.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import dev.pulse.core.data.CategoryChoice
import dev.pulse.core.platform.PlatformContext
import dev.pulse.core.prefs.UserRole
import dev.pulse.dsl.ThemeSpec
import dev.pulse.ui.components.CategoryPicker
import dev.pulse.ui.components.RolePicker
import dev.pulse.ui.design.PulseTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UserVisibleBehaviourTest {

    // ---- Issue 3: specificity picker ----

    @Test
    fun choosingACategoryReportsIt() = runComposeUiTest {
        var chosen: CategoryChoice? = null
        setContent { PulseTheme(ThemeSpec()) { CategoryPicker(onChoice = { chosen = it }, onOther = {}) } }
        onNodeWithTag("category-fashion").performClick()
        assertEquals("fashion", chosen?.id)
    }

    @Test
    fun otherOpensFreeTextAndSubmitsIt() = runComposeUiTest {
        var other: String? = null
        setContent { PulseTheme(ThemeSpec()) { CategoryPicker(onChoice = {}, onOther = { other = it }) } }
        onNodeWithTag("category-other-input").assertDoesNotExist()
        onNodeWithTag("category-other").performClick()
        onNodeWithTag("category-other-input").assertIsDisplayed()
        onNodeWithTag("category-other-input").performTextInput("pottery")
        onNodeWithTag("category-other-input").performImeAction()
        assertEquals("pottery", other)
    }

    // ---- Issue 4: role selection ----

    @Test
    fun rolePickerReportsTheChoice() = runComposeUiTest {
        var picked: UserRole? = null
        setContent { PulseTheme(ThemeSpec()) { RolePicker(current = null, onSelect = { picked = it }) } }
        onNodeWithTag("role-merchant").performClick()
        assertEquals(UserRole.MERCHANT, picked)
    }

    @Test
    fun roleQuestionShowsOnFirstLaunchOnlyAndSurvivesRestart() {
        val db = File.createTempFile("pulse-ui", ".db").apply { delete(); deleteOnExit() }
        val url = "jdbc:sqlite:${db.absolutePath}"

        runComposeUiTest {
            val graph = PulseGraph.create(PlatformContext(url), launcher = {}, useSampleData = true)
            setContent { PulseApp(graph) }
            onNodeWithText("Are you a Regular user, or a Merchant?").assertIsDisplayed()
            onNodeWithTag("role-regular").performClick()
            onNodeWithText("What's New").assertIsDisplayed()
        }

        // "App restart": a brand-new graph over the same database file.
        runComposeUiTest {
            val graph = PulseGraph.create(PlatformContext(url), launcher = {}, useSampleData = true)
            setContent { PulseApp(graph) }
            onNodeWithText("What's New").assertIsDisplayed()
            onNodeWithText("Are you a Regular user, or a Merchant?").assertDoesNotExist()
            assertEquals(UserRole.REGULAR, graph.roles.role.value)
        }
    }

    // ---- Card actions: Save (shelf) and Share (share sheet) ----

    private class RecordingLauncher : ActionLauncher {
        val shared = mutableListOf<Pair<String, String>>()
        override fun open(uri: String) = Unit
        override fun share(subject: String, text: String) { shared += subject to text }
    }

    private fun sampleItem(graph: PulseGraph) =
        graph.resolver.resolve(dev.pulse.dsl.Binding(dev.pulse.dsl.ListingQuery(listOf("food.cafe")))).first()

    @Test
    fun saveTogglesTheShelfAndSurvivesRestart() {
        val db = File.createTempFile("pulse-shelf", ".db").apply { delete(); deleteOnExit() }
        val url = "jdbc:sqlite:${db.absolutePath}"
        val graph = PulseGraph.create(PlatformContext(url), launcher = {}, useSampleData = true)
        val item = sampleItem(graph)
        val save = dev.pulse.dsl.ActionSpec(dev.pulse.dsl.ActionKind.SAVE, mapOf("listingId" to item.item.id))

        graph.dispatch(save, item)
        assertEquals(true, graph.session.isSaved(item.item.id))

        val restarted = PulseGraph.create(PlatformContext(url), launcher = {}, useSampleData = true)
        assertEquals(listOf(item.item.id), restarted.session.saved().map { it.id }, "shelf persists")

        restarted.dispatch(save, item)
        assertEquals(false, restarted.session.isSaved(item.item.id), "second tap removes it")
    }

    @Test
    fun shareOpensTheShareSheetWithNameAndMapLink() {
        val launcher = RecordingLauncher()
        val graph = PulseGraph.create(PlatformContext(), launcher = launcher, useSampleData = true)
        val item = sampleItem(graph)
        graph.dispatch(dev.pulse.dsl.ActionSpec(dev.pulse.dsl.ActionKind.SHARE, mapOf("listingId" to item.item.id)), item)
        val (subject, text) = launcher.shared.single()
        assertEquals(item.item.title("en"), subject)
        assert("openstreetmap.org" in text && subject in text) { text }
    }

    @Test
    fun tappingTheBookmarkShowsConfirmation() = runComposeUiTest {
        val graph = PulseGraph.create(PlatformContext(), launcher = {}, useSampleData = true)
        graph.roles.setRole(UserRole.REGULAR)
        kotlinx.coroutines.runBlocking { graph.pipeline.submit(dev.pulse.core.ai.IntentInput("new cafes in Koramangala within 3 km")) }
        setContent { PulseApp(graph, startScreen = Screen.Discover) }
        onAllNodesWithContentDescription("Save")[0].performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithText("Saved to your shelf").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Remove from saved")[0].assertExists()
    }
}
