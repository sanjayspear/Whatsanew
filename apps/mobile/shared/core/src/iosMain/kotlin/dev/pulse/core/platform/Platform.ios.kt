package dev.pulse.core.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.pulse.core.ai.AiEngine
import dev.pulse.core.ai.BackendAvailability
import dev.pulse.core.ai.GenerationRequest
import dev.pulse.core.ai.LanguageModelBackend
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.ai.SpeechEngine
import dev.pulse.core.ai.ThermalMonitor
import dev.pulse.core.ai.TieredAiEngine
import dev.pulse.core.ai.Transcript
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.model.SignatureVerifier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine

actual class PlatformContext(
    val llm: NativeLlmBridge? = null,
    val speech: NativeSpeechBridge? = null,
    val crypto: NativeCryptoBridge? = null,
)

/**
 * iOS ladder:
 *  1. Apple Foundation Models (on-device ~3B, iOS 26+ on Apple Intelligence devices), via Swift bridge.
 *  2. (P3) llama.cpp + Gemma INT4 GGUF via cinterop for older devices.
 *  3. Rules: always.
 */
actual fun createPlatformAiEngine(context: PlatformContext, gazetteer: Gazetteer): AiEngine = TieredAiEngine(
    rules = RuleBasedIntentParser(gazetteer),
    ladder = listOfNotNull(context.llm?.let(::FoundationModelsBackend)),
    thermal = ThermalMonitor { context.llm?.isThermallyThrottled() ?: false },
)

private class FoundationModelsBackend(private val bridge: NativeLlmBridge) : LanguageModelBackend {
    override val id = "apple-foundation-models"
    override suspend fun availability() =
        if (bridge.isAvailable()) BackendAvailability.AVAILABLE else BackendAvailability.UNAVAILABLE

    override fun generate(request: GenerationRequest): Flow<String> = flow {
        val text = suspendCancellableCoroutine { cont ->
            bridge.generate(request.systemInstruction, request.prompt) { result, error ->
                if (result != null) cont.resume(result) else cont.resumeWithException(IllegalStateException(error ?: "generation failed"))
            }
        }
        emit(text)
    }
}

actual fun createSpeechEngine(context: PlatformContext): SpeechEngine {
    val bridge = context.speech ?: return NoSpeechEngine
    return object : SpeechEngine {
        override val isAvailable get() = bridge.isAvailable()
        override fun listen(languageTag: String): Flow<Transcript> = callbackFlow {
            bridge.start(
                languageTag,
                onPartial = { trySend(Transcript.Partial(it)) },
                onFinal = { text, confidence -> trySend(Transcript.Final(text, confidence)); close() },
                onError = { close(IllegalStateException(it)) },
            )
            awaitClose { bridge.stop() }
        }
        override fun stop() = bridge.stop()
    }
}

@OptIn(ExperimentalEncodingApi::class)
actual fun createSignatureVerifier(context: PlatformContext, publicKeyBase64: String): SignatureVerifier {
    val crypto = checkNotNull(context.crypto) { "NativeCryptoBridge is required on iOS" }
    return SignatureVerifier { payload, signature -> crypto.verifyEd25519(Base64.encode(payload), signature, publicKeyBase64) }
}

actual fun createSqlDriver(context: PlatformContext): SqlDriver = NativeSqliteDriver(PulseDatabase.Schema, "pulse.db")
