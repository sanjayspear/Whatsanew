package dev.pulse.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SurfaceComposerTest {

    private var seq = 0
    private val composer = SurfaceComposer { "s${++seq}" }
    private val catalog = ComponentCatalog { true }
    private val validator = DslValidator(catalog)

    private fun discover(query: ListingQuery, prefs: LayoutPrefs = LayoutPrefs(), current: Surface? = null): Surface {
        val result = composer.compose(IntentFrame(Task.DISCOVER, query = query), current, prefs)
        assertFalse(validator.validate(result.patch).hasErrors(), "composer output must always validate")
        return assertIs<ReduceResult.Applied>(SurfaceReducer.reduce(current, result.patch)).surface
    }

    private fun Surface.roles() = root.children.map { it.role }

    @Test
    fun discoverPaintsHeaderMapResultsInDefaultOrder() {
        val s = discover(ListingQuery(categories = listOf("food.cafe")))
        assertEquals(listOf(Roles.HEADER, Roles.MAP, Roles.RESULTS), s.roles())
    }

    @Test
    fun unanchoredQueryDefaultsToHere() {
        val s = discover(ListingQuery(categories = listOf("food.cafe")))
        assertEquals(GeoAnchor.Here, s.root.findByRole(Roles.RESULTS)!!.bind!!.query.near!!.anchor)
    }

    @Test
    fun eventLikeQueriesAddVideosBookingAndSoonestRanking() {
        val s = discover(ListingQuery(categories = listOf("arts.theatre"), time = TimeWindow(TimePreset.THIS_WEEKEND)))
        assertTrue(Roles.VIDEOS in s.roles())
        val results = s.root.findByRole(Roles.RESULTS)!!.bind!!
        assertEquals(RankStrategy.SOONEST, results.rank.strategy)
        assertEquals(ActionKind.BOOK, results.template!!.actions.first().kind)
    }

    @Test
    fun layoutPrefsAreHonouredOnEveryNewSurface() {
        val prefs = LayoutPrefs().apply(LayoutEdit.Move(Roles.MAP, 0)).apply(LayoutEdit.Hide(Roles.HEADER))
        val s = discover(ListingQuery(categories = listOf("food.cafe")), prefs)
        assertEquals(listOf(Roles.MAP, Roles.RESULTS), s.roles())
    }

    @Test
    fun customizeLayoutEmitsMoveOpAndPersistsPrefs() {
        val current = discover(ListingQuery(categories = listOf("food.cafe")))
        val result = composer.compose(
            IntentFrame(Task.CUSTOMIZE_LAYOUT, layout = listOf(LayoutEdit.Move(Roles.MAP, 0))),
            current,
            LayoutPrefs(),
        )
        assertEquals(Persistence.PROFILE, result.patch.persistence)
        assertEquals(Roles.MAP, result.prefs.order.first())
        val s = assertIs<ReduceResult.Applied>(SurfaceReducer.reduce(current, result.patch)).surface
        assertEquals(Roles.MAP, s.roles().first())
    }

    @Test
    fun showingAHiddenSectionRebuildsTheSurface() {
        val hiddenMap = LayoutPrefs(hidden = setOf(Roles.MAP))
        val current = discover(ListingQuery(categories = listOf("food.cafe")), hiddenMap)
        val result = composer.compose(
            IntentFrame(Task.CUSTOMIZE_LAYOUT, layout = listOf(LayoutEdit.Show(Roles.MAP))),
            current,
            hiddenMap,
        )
        val s = assertIs<ReduceResult.Applied>(SurfaceReducer.reduce(current, result.patch)).surface
        assertTrue(Roles.MAP in s.roles())
        assertEquals(current.id, s.id, "rebuild keeps surface identity")
    }

    @Test
    fun refineNarrowsEveryBindingAndKeepsTheRestOfTheQuery() {
        val current = discover(ListingQuery(categories = listOf("food.cafe"), freshness = Freshness.NEW))
        val result = composer.compose(IntentFrame(Task.REFINE, query = ListingQuery(openNow = true)), current, LayoutPrefs())
        val s = assertIs<ReduceResult.Applied>(SurfaceReducer.reduce(current, result.patch)).surface
        val q = s.root.findByRole(Roles.RESULTS)!!.bind!!.query
        assertEquals(true, q.openNow)
        assertEquals(Freshness.NEW, q.freshness)
        assertEquals(listOf("food.cafe"), q.categories)
        assertEquals(true, s.root.findByRole(Roles.MAP)!!.bind!!.query.openNow)
    }

    @Test
    fun unknownIntentShowsSuggestionsWithoutDestroyingCurrentSurface() {
        val current = discover(ListingQuery(categories = listOf("food.cafe")))
        val result = composer.compose(IntentFrame(Task.UNKNOWN), current, LayoutPrefs())
        val s = assertIs<ReduceResult.Applied>(SurfaceReducer.reduce(current, result.patch)).surface
        assertEquals(Roles.SUGGESTIONS, s.roles().first())
        assertNotNull(s.root.findByRole(Roles.RESULTS))
        // Asking again doesn't stack suggestion rows.
        val again = composer.compose(IntentFrame(Task.UNKNOWN), s, LayoutPrefs())
        val s2 = assertIs<ReduceResult.Applied>(SurfaceReducer.reduce(s, again.patch)).surface
        assertEquals(1, s2.root.children.count { it.role == Roles.SUGGESTIONS })
    }

    @Test
    fun themeIntentIsAProfilePersistedThemeOp() {
        val result = composer.compose(
            IntentFrame(Task.CUSTOMIZE_THEME, theme = ThemePatch(mood = Mood.WARM)),
            null,
            LayoutPrefs(),
        )
        assertEquals(Persistence.PROFILE, result.patch.persistence)
        assertIs<UiOp.UpdateTheme>(result.patch.ops.single())
    }
}
