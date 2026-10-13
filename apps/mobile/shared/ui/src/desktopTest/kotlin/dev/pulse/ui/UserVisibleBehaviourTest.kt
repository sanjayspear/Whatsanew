package dev.pulse.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
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
    fun accountSetupShowsOnFirstLaunchOnlyAndSurvivesRestart() {
        val db = File.createTempFile("pulse-ui", ".db").apply { delete(); deleteOnExit() }
        val url = "jdbc:sqlite:${db.absolutePath}"

        runComposeUiTest {
            val graph = PulseGraph.create(PlatformContext(url), launcher = {}, useSampleData = true)
            setContent { PulseApp(graph) }
            onNodeWithText("Are you a User, or a Merchant?").assertIsDisplayed()
            onNodeWithTag("profile-save").performScrollTo().performClick()
            onNodeWithText("Please enter your name").assertExists()
            onNodeWithTag("profile-name").performTextInput("Asha")
            onNodeWithTag("role-regular").performScrollTo().performClick()
            onNodeWithTag("profile-save").performScrollTo().performClick()
            onNodeWithTag("tagline").assertIsDisplayed()
            onNodeWithText("Search Recently Launched").assertIsDisplayed()
        }

        // "App restart": a brand-new graph over the same database file.
        runComposeUiTest {
            val graph = PulseGraph.create(PlatformContext(url), launcher = {}, useSampleData = true)
            setContent { PulseApp(graph) }
            onNodeWithTag("tagline").assertIsDisplayed()
            onNodeWithText("Are you a User, or a Merchant?").assertDoesNotExist()
            assertEquals(UserRole.REGULAR, graph.roles.role.value)
            assertEquals("Asha", graph.profiles.profile.value?.name)
        }
    }

    // ---- Card actions: Save (shelf) and Share (share sheet) ----

    private class RecordingLauncher : ActionLauncher {
        val shared = mutableListOf<Pair<String, String>>()
        val opened = mutableListOf<String>()
        override fun open(uri: String) { opened += uri }
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
        val graph = signedIn()
        kotlinx.coroutines.runBlocking { graph.pipeline.submit(dev.pulse.core.ai.IntentInput("new cafes in Koramangala within 3 km")) }
        setContent { PulseApp(graph, startScreen = Screen.Discover) }
        onAllNodesWithContentDescription("Save")[0].performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithText("Saved to your shelf").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Remove from saved")[0].assertExists()
    }

    // ---- Search Around requirements ----

    private fun signedIn(role: UserRole = UserRole.REGULAR, launcher: ActionLauncher = ActionLauncher {}) =
        PulseGraph.create(PlatformContext(), launcher = launcher, useSampleData = true).apply {
            roles.setRole(role)
            profiles.save(dev.pulse.core.prefs.UserProfile("Asha"))
        }

    @Test
    fun clearSearchReturnsToTheEmptySearchPage() = runComposeUiTest {
        val graph = signedIn()
        kotlinx.coroutines.runBlocking { graph.pipeline.submit(dev.pulse.core.ai.IntentInput("theatre near Ravindra Kalakshetra")) }
        setContent { PulseApp(graph, startScreen = Screen.Discover) }
        onNodeWithTag("results-summary").assertExists()
        onNodeWithTag("clear-search").performClick()
        onNodeWithText("TRY SEARCHING").assertIsDisplayed()
        onNodeWithTag("results-summary").assertDoesNotExist()
        assertEquals(null, graph.pipeline.state.value.surface)
    }

    @Test
    fun bookNowOpensTheBookingPageAndTheChosenPlatform() = runComposeUiTest {
        val launcher = RecordingLauncher()
        val graph = signedIn(launcher = launcher)
        kotlinx.coroutines.runBlocking { graph.pipeline.submit(dev.pulse.core.ai.IntentInput("theatre near Ravindra Kalakshetra")) }
        setContent { PulseApp(graph, startScreen = Screen.Discover) }
        onAllNodesWithText("Book Now")[0].performClick()
        onNodeWithText("Choose where to book").assertIsDisplayed()
        onNodeWithText("BookMyShow").assertExists()
        onNodeWithText("District by Zomato").assertExists()
        onNodeWithText("You searched for “theatre near Ravindra Kalakshetra”").assertExists()
        onNodeWithTag("booking-option-0").performClick()
        assertEquals(listOf("https://in.bookmyshow.com/explore/plays-bengaluru"), launcher.opened)
    }

    @Test
    fun cafesHaveNoBookNow() = runComposeUiTest {
        val graph = signedIn()
        kotlinx.coroutines.runBlocking { graph.pipeline.submit(dev.pulse.core.ai.IntentInput("cafes in Koramangala")) }
        setContent { PulseApp(graph, startScreen = Screen.Discover) }
        onNodeWithTag("results-summary").assertExists()
        onAllNodesWithText("Book Now").assertCountEquals(0)
    }

    @Test
    fun emptyResultsOfferAWiderSearchInsteadOfWideningSilently() = runComposeUiTest {
        val graph = signedIn()
        kotlinx.coroutines.runBlocking { graph.pipeline.submit(dev.pulse.core.ai.IntentInput("temples near me")) }
        setContent { PulseApp(graph, startScreen = Screen.Discover) }
        onNodeWithTag("expand-prompt").assertIsDisplayed()
        onNodeWithText("No temples within 6 km.").assertExists()
        onNodeWithTag("expand-10").performClick()
        val near = graph.pipeline.state.value.surface!!.root.children.first { it.role == dev.pulse.dsl.Roles.RESULTS }.bind!!.query.near!!
        assertEquals(10_000, near.radiusMeters)
    }

    @Test
    fun ambiguousSearchAsksAQuestion() = runComposeUiTest {
        val graph = signedIn()
        setContent { PulseApp(graph, startScreen = Screen.Discover) }
        onNodeWithTag("intent-input").performTextInput("hotels near me")
        onNodeWithTag("intent-input").performImeAction()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithText("Do you mean a place to stay or a place to eat?").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("clarify-1").performClick()
        waitUntil(timeoutMillis = 3_000) { graph.pipeline.state.value.lastUtterance == "restaurants near me" }
    }

    @Test
    fun theHomeScreenHasTheNewLabelsAndTagline() = runComposeUiTest {
        setContent { PulseApp(signedIn()) }
        onNodeWithText("One Search.\nEndless Possibilities.").assertIsDisplayed()
        onNodeWithTag("door-search-around").assertIsDisplayed()
        onNodeWithTag("door-recently-launched").assertIsDisplayed()
        onAllNodesWithText("What's New").assertCountEquals(0)
        onAllNodesWithText("Legacy").assertCountEquals(0)
    }

    @Test
    fun theMenuListsTheRequiredSectionsByRole() = runComposeUiTest {
        val graph = signedIn(UserRole.MERCHANT)
        setContent { PulseApp(graph) }
        onNodeWithTag("menu-button").performClick()
        listOf(
            "menu-profile", "menu-search", "menu-recent", "menu-history", "menu-saved", "menu-settings",
            "menu-help", "menu-about", "menu-legal", "menu-signout", "menu-publish", "menu-posts",
        ).forEach { onNodeWithTag(it).assertExists() }
        onNodeWithTag("menu-about").performClick()
        onNodeWithText("About Search Around").assertIsDisplayed()
    }

    @Test
    fun regularUsersDontSeeMerchantEntries() = runComposeUiTest {
        setContent { PulseApp(signedIn(UserRole.REGULAR)) }
        onNodeWithTag("menu-button").performClick()
        onNodeWithTag("menu-publish").assertDoesNotExist()
    }

    @Test
    fun signOutReturnsToAccountSetup() = runComposeUiTest {
        val graph = signedIn()
        setContent { PulseApp(graph) }
        onNodeWithTag("menu-button").performClick()
        onNodeWithTag("menu-signout").performClick()
        onNodeWithText("Are you a User, or a Merchant?").assertIsDisplayed()
        assertEquals(null, graph.profiles.profile.value)
    }

    @Test
    fun merchantsPublishAndTheOfferingAppearsInRecentlyLaunched() = runComposeUiTest {
        val graph = signedIn(UserRole.MERCHANT)
        val r = graph.session.publish(
            dev.pulse.core.data.MerchantDraft(
                title = "Juice Junction opening", type = dev.pulse.core.data.OfferingType.SHOP,
                description = "Fresh juices, opening offer", area = "Koramangala",
            ),
            null,
        )
        assert(r is dev.pulse.core.data.MerchantPosts.Outcome.Published) { r.toString() }
        graph.settings.setManualArea("Koramangala")
        setContent { PulseApp(graph) }
        onNodeWithTag("door-recently-launched").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithText("Juice Junction opening").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Launched", substring = true)[0].assertExists()
    }
}
