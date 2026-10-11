package dev.pulse.intent.domain

import dev.pulse.dsl.ComponentCatalog
import dev.pulse.dsl.DslValidator
import dev.pulse.dsl.IntentFrame
import dev.pulse.dsl.LayoutPrefs
import dev.pulse.dsl.SurfaceComposer
import dev.pulse.dsl.UiPatch
import dev.pulse.dsl.hasErrors
import java.util.UUID

/** Outbound port: the server LLM (served by ai-server / Triton) that turns text into a frame. */
fun interface FrameModel {
    fun frameFor(utterance: String, languageTag: String): IntentFrame
}

/**
 * Same composer + validator as the phone, so a server-resolved surface is byte-for-byte what
 * the device would have painted. Parity is a test, not a hope.
 */
class ResolveIntent(private val model: FrameModel, catalog: ComponentCatalog) {
    private val composer = SurfaceComposer { UUID.randomUUID().toString() }
    private val validator = DslValidator(catalog)

    fun resolve(utterance: String, languageTag: String, prefs: LayoutPrefs): UiPatch {
        val frame = model.frameFor(utterance, languageTag)
        val patch = composer.compose(frame, current = null, prefs = prefs).patch
        require(!validator.validate(patch).hasErrors()) { "model produced an invalid surface" }
        return patch
    }
}
