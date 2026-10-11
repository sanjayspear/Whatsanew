package dev.pulse.dsl

data class ValidationIssue(val path: String, val message: String, val severity: Severity) {
    enum class Severity {
        /** Rejects the patch. */
        ERROR,
        /** Rendered with graceful degradation (e.g. unknown component is skipped). */
        WARNING,
    }
}

fun List<ValidationIssue>.hasErrors(): Boolean = any { it.severity == ValidationIssue.Severity.ERROR }

/**
 * Structural and safety validation, run on every patch before it reaches the reducer, whether
 * the patch came from rules, the on-device model, or the server.
 *
 * Unknown components are *warnings*, not errors. An older app receiving a surface from a newer
 * model skips what it can't draw instead of failing the whole screen (forward compatibility).
 */
class DslValidator(
    private val catalog: ComponentCatalog,
    private val limits: Limits = Limits(),
) {
    data class Limits(val maxNodes: Int = 200, val maxDepth: Int = 10)

    fun validate(patch: UiPatch): List<ValidationIssue> = buildList {
        if (patch.schemaVersion > DSL_VERSION) {
            add(error("patch", "schemaVersion ${patch.schemaVersion} is newer than $DSL_VERSION"))
        }
        patch.ops.forEachIndexed { i, op ->
            val path = "ops[$i]"
            when (op) {
                is UiOp.ReplaceSurface -> addAll(validate(op.surface, path))
                is UiOp.InsertNode -> addAll(validateTree(op.node, "$path.node"))
                is UiOp.UpdateNode -> op.bind?.let { addAll(validateBinding(it, "$path.bind")) }
                is UiOp.UpdateTheme -> op.patch.accentHex?.let {
                    if (!isHexColor(it)) add(error("$path.patch.accentHex", "'$it' is not #RRGGBB"))
                }
                is UiOp.MoveNode, is UiOp.RemoveNode -> Unit // existence is checked by the reducer
            }
        }
    }

    fun validate(surface: Surface, path: String = "surface"): List<ValidationIssue> = buildList {
        if (!isHexColor(surface.theme.accentHex)) {
            add(error("$path.theme.accentHex", "'${surface.theme.accentHex}' is not #RRGGBB"))
        }
        addAll(validateTree(surface.root, "$path.root"))
    }

    private fun validateTree(root: Node, path: String): List<ValidationIssue> = buildList {
        val all = root.walk().toList()
        if (all.size > limits.maxNodes) add(error(path, "${all.size} nodes exceeds ${limits.maxNodes}"))
        if (root.depth() > limits.maxDepth) add(error(path, "depth exceeds ${limits.maxDepth}"))
        all.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            add(error(path, "duplicate node id '$it'"))
        }
        all.forEach { node ->
            val nodePath = "$path#${node.id}"
            if (node.id.isBlank()) add(error(nodePath, "blank node id"))
            if (!catalog.supports(node.component)) {
                add(warning(nodePath, "unknown component '${node.component}' will be skipped"))
            }
            node.bind?.let { addAll(validateBinding(it, "$nodePath.bind")) }
        }
    }

    private fun validateBinding(binding: Binding, path: String): List<ValidationIssue> = buildList {
        if (binding.limit !in 1..Binding.MAX_LIMIT) {
            add(error(path, "limit ${binding.limit} outside 1..${Binding.MAX_LIMIT}"))
        }
        binding.query.near?.radiusMeters?.let {
            if (it !in GeoFilter.MIN_RADIUS_METERS..GeoFilter.MAX_RADIUS_METERS) {
                add(error("$path.query.near", "radius ${it}m outside allowed range"))
            }
        }
        binding.query.priceLevelMax?.let {
            if (it !in 1..4) add(error("$path.query.priceLevelMax", "must be 1..4"))
        }
        binding.template?.let { template ->
            if (template.walk().any { it.bind != null }) {
                add(error("$path.template", "templates must not contain nested bindings"))
            }
        }
    }

    private fun error(path: String, msg: String) = ValidationIssue(path, msg, ValidationIssue.Severity.ERROR)
    private fun warning(path: String, msg: String) = ValidationIssue(path, msg, ValidationIssue.Severity.WARNING)
}
