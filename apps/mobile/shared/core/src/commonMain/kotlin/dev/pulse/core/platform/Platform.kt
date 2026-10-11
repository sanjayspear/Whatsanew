package dev.pulse.core.platform

import app.cash.sqldelight.db.SqlDriver
import dev.pulse.core.ai.AiEngine
import dev.pulse.core.ai.SpeechEngine
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.model.SignatureVerifier

/**
 * Platform handle. On Android it wraps `Context`. On iOS it carries Swift-implemented bridges
 * (Apple Foundation Models, Speech, CryptoKit are Swift-only APIs, so Kotlin/Native cannot call
 * them directly; Swift implements a Kotlin interface and hands it over at startup).
 */
expect class PlatformContext

/** Builds the per-platform model ladder (see each `actual` for the exact rungs). */
expect fun createPlatformAiEngine(context: PlatformContext, gazetteer: Gazetteer): AiEngine

expect fun createSpeechEngine(context: PlatformContext): SpeechEngine

expect fun createSignatureVerifier(context: PlatformContext, publicKeyBase64: String): SignatureVerifier

expect fun createSqlDriver(context: PlatformContext): SqlDriver

/** Speech is optional on some platforms (desktop dev harness): an always-unavailable engine. */
object NoSpeechEngine : SpeechEngine {
    override val isAvailable = false
    override fun listen(languageTag: String) = kotlinx.coroutines.flow.emptyFlow<dev.pulse.core.ai.Transcript>()
    override fun stop() = Unit
}
