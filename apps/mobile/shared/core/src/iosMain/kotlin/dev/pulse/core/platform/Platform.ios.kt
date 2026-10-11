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
import dev.pulse.core.data.LocationTracker
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.model.ModelManager
import dev.pulse.core.model.ModelState
import dev.pulse.core.model.OnDeviceModel
import dev.pulse.core.model.SignatureVerifier
import dev.pulse.core.model.UnsupportedModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val mediaPipe: NativeMediaPipeBridge? = null,
    val modelStore: NativeModelBridge? = null,
)

/**
 * iOS ladder (same universal model as Android):
 *  1. Qwen2.5-0.5B (Apache-2.0) via MediaPipe LLM Inference: every supported iPhone, downloaded once.
 *  2. Apple Foundation Models: optional accelerator on Apple Intelligence devices.
 *  3. Rules: always.
 */
actual fun createPlatformAiEngine(context: PlatformContext, gazetteer: Gazetteer, models: ModelManager): AiEngine = TieredAiEngine(
    rules = RuleBasedIntentParser(gazetteer),
    ladder = listOfNotNull(
        context.mediaPipe?.let { MediaPipeIosBackend(it, models) },
        context.llm?.let(::FoundationModelsBackend),
    ),
    thermal = ThermalMonitor { context.llm?.isThermallyThrottled() ?: false },
)

actual fun createModelManager(context: PlatformContext, model: OnDeviceModel): ModelManager =
    context.modelStore?.let { IosModelManager(it, model) } ?: UnsupportedModelManager(model)

private class MediaPipeIosBackend(private val bridge: NativeMediaPipeBridge, private val models: ModelManager) : LanguageModelBackend {
    override val id = "mediapipe-" + models.model.id
    override suspend fun availability() =
        if (models.installedPath() != null) BackendAvailability.AVAILABLE else BackendAvailability.DOWNLOADABLE

    override suspend fun prepare() {
        check(bridge.load(checkNotNull(models.installedPath()))) { "MediaPipe failed to load model" }
    }

    override fun generate(request: GenerationRequest): Flow<String> = flow {
        val prompt = models.model.promptFormat.format(request.systemInstruction, request.prompt)
        emit(suspendCancellableCoroutine { cont ->
            bridge.generate(prompt) { text, error ->
                if (text != null) cont.resume(text) else cont.resumeWithException(IllegalStateException(error ?: "generation failed"))
            }
        })
    }

    override fun close() = bridge.close()
}

private class IosModelManager(private val bridge: NativeModelBridge, override val model: OnDeviceModel) : ModelManager {
    private val _state = MutableStateFlow<ModelState>(if (bridge.installedPath(model.id) != null) ModelState.Ready else ModelState.NotInstalled)
    override val state: StateFlow<ModelState> = _state.asStateFlow()
    override fun installedPath(): String? = bridge.installedPath(model.id)

    override suspend fun download() {
        if (installedPath() != null) { _state.value = ModelState.Ready; return }
        suspendCancellableCoroutine { cont ->
            bridge.download(
                model.id, model.url, model.sha256,
                onProgress = { _state.value = if (it >= 1.0) ModelState.Verifying else ModelState.Downloading(it.toFloat()) },
                onDone = { _, error ->
                    _state.value = if (error == null) ModelState.Ready else ModelState.Failed(error)
                    cont.resume(Unit)
                },
            )
        }
    }

    override fun delete() {
        bridge.delete(model.id)
        _state.value = ModelState.NotInstalled
    }
}

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

actual fun createLocationTracker(context: PlatformContext): LocationTracker = IosLocationTracker()

actual fun createHttpClient(): io.ktor.client.HttpClient = io.ktor.client.HttpClient(io.ktor.client.engine.darwin.Darwin) {
    install(io.ktor.client.plugins.HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
    }
}
