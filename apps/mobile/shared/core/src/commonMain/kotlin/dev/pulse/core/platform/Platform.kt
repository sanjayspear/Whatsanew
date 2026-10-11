package dev.pulse.core.platform

import app.cash.sqldelight.db.SqlDriver
import dev.pulse.core.ai.AiEngine
import dev.pulse.core.ai.SpeechEngine
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.LocationTracker
import dev.pulse.core.model.ModelManager
import dev.pulse.core.model.OnDeviceModel
import dev.pulse.core.model.OnDeviceModels
import dev.pulse.core.model.SignatureVerifier

/**
 * Platform handle. On Android it wraps `Context`. On iOS it carries Swift-implemented bridges
 * (Apple Foundation Models, Speech, CryptoKit are Swift-only APIs, so Kotlin/Native cannot call
 * them directly; Swift implements a Kotlin interface and hands it over at startup).
 */
expect class PlatformContext

/** Builds the per-platform model ladder (see each `actual` for the exact rungs). */
expect fun createPlatformAiEngine(context: PlatformContext, gazetteer: Gazetteer, models: ModelManager): AiEngine

/** Where and how the universal on-device model is downloaded on this platform. */
expect fun createModelManager(context: PlatformContext, model: OnDeviceModel = OnDeviceModels.Default): ModelManager

expect fun createSpeechEngine(context: PlatformContext): SpeechEngine

expect fun createSignatureVerifier(context: PlatformContext, publicKeyBase64: String): SignatureVerifier

expect fun createSqlDriver(context: PlatformContext): SqlDriver

/** HTTP client with the platform engine (OkHttp on Android/desktop, NSURLSession on iOS). */
expect fun createHttpClient(): io.ktor.client.HttpClient

/** Live location (Android LocationManager / iOS CoreLocation / fixed on desktop). */
expect fun createLocationTracker(context: PlatformContext): LocationTracker

/** Speech is optional on some platforms (desktop dev harness): an always-unavailable engine. */
object NoSpeechEngine : SpeechEngine {
    override val isAvailable = false
    override fun listen(languageTag: String) = kotlinx.coroutines.flow.emptyFlow<dev.pulse.core.ai.Transcript>()
    override fun stop() = Unit
}
