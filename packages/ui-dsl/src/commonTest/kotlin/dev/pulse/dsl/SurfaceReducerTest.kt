package dev.pulse.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SurfaceReducerTest {

    private val surface = Surface(
        id = "s1",
        root = Node(
            "root", "layout.stack",
            children = listOf(
                Node("header", "hero.header", role = Roles.HEADER),
                Node("map", "map.stack", role = Roles.MAP),
                Node("results", "list.cards", role = Roles.RESULTS),
            ),
        ),
    )

    private fun applied(patch: UiPatch, from: Surface? = surface): Surface =
        assertIs<ReduceResult.Applied>(SurfaceReducer.reduce(from, patch)).surface

    private fun Surface.childIds() = root.children.map { it.id }

    @Test
    fun moveReordersSiblings() {
        val result = applied(UiPatch(listOf(UiOp.MoveNode("map", "root", 0))))
        assertEquals(listOf("map", "header", "results"), result.childIds())
    }

    @Test
    fun insertWithNegativeIndexAppends() {
        val result = applied(UiPatch(listOf(UiOp.InsertNode("root", Node("v", "list.videos")))))
        assertEquals(listOf("header", "map", "results", "v"), result.childIds())
    }

    @Test
    fun updateMergesPropsShallowly() {
        val withProps = applied(
            UiPatch(listOf(UiOp.UpdateNode("header", props = JsonObject(mapOf("a" to JsonPrimitive(1)))))),
        )
        val result = applied(
            UiPatch(listOf(UiOp.UpdateNode("header", props = JsonObject(mapOf("b" to JsonPrimitive(2)))))),
            withProps,
        )
        assertEquals(setOf("a", "b"), result.root.findById("header")!!.props.keys)
    }

    @Test
    fun themeUpdateIsClamped() {
        val result = applied(UiPatch(listOf(UiOp.UpdateTheme(ThemePatch(typeScale = 9f, motionScale = -1f)))))
        assertEquals(ThemeBounds.type.endInclusive, result.theme.typeScale)
        assertEquals(0f, result.theme.motionScale)
    }

    @Test
    fun patchIsAtomicOneBadOpRejectsEverything() {
        val patch = UiPatch(
            listOf(
                UiOp.MoveNode("map", "root", 0),
                UiOp.RemoveNode("does-not-exist"),
            ),
        )
        val rejected = assertIs<ReduceResult.Rejected>(SurfaceReducer.reduce(surface, patch))
        assertEquals(1, rejected.opIndex)
    }

    @Test
    fun cannotMoveANodeIntoItsOwnSubtree() {
        val nested = surface.copy(
            root = surface.root.mapNode("map") { it.copy(children = listOf(Node("pin", "map.pin"))) },
        )
        val patch = UiPatch(listOf(UiOp.MoveNode("map", "pin", 0)))
        assertIs<ReduceResult.Rejected>(SurfaceReducer.reduce(nested, patch))
    }

    @Test
    fun cannotRemoveRootOrInsertDuplicateIds() {
        assertIs<ReduceResult.Rejected>(SurfaceReducer.reduce(surface, UiPatch(listOf(UiOp.RemoveNode("root")))))
        assertIs<ReduceResult.Rejected>(
            SurfaceReducer.reduce(surface, UiPatch(listOf(UiOp.InsertNode("root", Node("map", "x"))))),
        )
    }

    @Test
    fun newerSchemaVersionsAreRejected() {
        assertIs<ReduceResult.Rejected>(SurfaceReducer.reduce(surface, UiPatch(emptyList(), schemaVersion = 99)))
    }
}
