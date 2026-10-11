package dev.pulse.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DslValidatorTest {

    private val known = setOf("layout.stack", "glass.card", "list.cards")
    private val validator = DslValidator({ it in known })

    private fun surfaceOf(vararg children: Node) =
        Surface("s", Node("root", "layout.stack", children = children.toList()))

    @Test
    fun unknownComponentsDegradeToWarnings() {
        val issues = validator.validate(surfaceOf(Node("x", "holo.projector")))
        assertFalse(issues.hasErrors())
        assertEquals(ValidationIssue.Severity.WARNING, issues.single().severity)
    }

    @Test
    fun duplicateIdsAreErrors() {
        assertTrue(validator.validate(surfaceOf(Node("a", "glass.card"), Node("a", "glass.card"))).hasErrors())
    }

    @Test
    fun bindingLimitsAndRadiusAreEnforced() {
        val huge = Binding(ListingQuery(near = GeoFilter(GeoAnchor.Here, radiusMeters = 900_000)), limit = 500)
        val issues = validator.validate(surfaceOf(Node("r", "list.cards", bind = huge)))
        assertEquals(2, issues.count { it.severity == ValidationIssue.Severity.ERROR })
    }

    @Test
    fun templatesCannotNestBindings() {
        val nested = Binding(ListingQuery(), template = Node("t", "glass.card", bind = Binding(ListingQuery())))
        assertTrue(validator.validate(surfaceOf(Node("r", "list.cards", bind = nested))).hasErrors())
    }

    @Test
    fun nodeCountLimitProtectsTheRenderer() {
        val tiny = DslValidator({ true }, DslValidator.Limits(maxNodes = 3))
        val issues = tiny.validate(surfaceOf(Node("a", "x"), Node("b", "x"), Node("c", "x")))
        assertTrue(issues.hasErrors())
    }

    @Test
    fun badAccentColorInThemePatchIsAnError() {
        val patch = UiPatch(listOf(UiOp.UpdateTheme(ThemePatch(accentHex = "orange"))))
        assertTrue(validator.validate(patch).hasErrors())
    }
}
