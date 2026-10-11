package dev.pulse.core.platform

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dev.pulse.core.ai.BackendAvailability
import dev.pulse.core.ai.GenerationRequest
import dev.pulse.core.ai.LanguageModelBackend
import dev.pulse.core.ai.SpeechEngine
import dev.pulse.core.ai.Transcript
import dev.pulse.core.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Gemini Nano through AICore (ML Kit GenAI Prompt API).
 *
 * INTEGRATION POINT (P2): add `com.google.mlkit:genai-prompt` once the device allowlist for
 * the target market is confirmed, then implement [availability] with the client's feature-status
 * check and [generate] with its streaming call. Until then this rung reports UNAVAILABLE and the
 * ladder falls through to MediaPipe, so app behaviour stays correct either way.
 */
class GeminiNanoBackend(@Suppress("unused") private val context: Context) : LanguageModelBackend {
    override val id = "gemini-nano-aicore"
    override suspend fun availability() = BackendAvailability.UNAVAILABLE
    override fun generate(request: GenerationRequest): Flow<String> = emptyFlow()
}

/** The universal open-source model (`.task` bundle) via MediaPipe LLM Inference. */
class MediaPipeLlmBackend(
    private val context: Context,
    private val models: ModelManager,
) : LanguageModelBackend {
    override val id = "mediapipe-" + models.model.id
    private var engine: LlmInference? = null

    override suspend fun availability(): BackendAvailability {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        // A 0.5B model peaks around 1 GB; leave the rest of a 4 GB phone to the OS and the app.
        val enoughRam = mem.totalMem >= 3_500_000_000L && !am.isLowRamDevice
        return when {
            !enoughRam -> BackendAvailability.UNAVAILABLE
            models.installedPath() != null -> BackendAvailability.AVAILABLE
            else -> BackendAvailability.DOWNLOADABLE
        }
    }

    /** Which accelerator actually loaded, for diagnostics ("gpu" or "cpu"). */
    var accelerator: String = "none"
        private set

    override suspend fun prepare() = withContext(Dispatchers.Default) {
        if (engine != null) return@withContext
        val path = checkNotNull(models.installedPath()) { "model not installed" }
        fun build(backend: LlmInference.Backend) = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .setPreferredBackend(backend)
                .build(),
        )
        // A backend only counts once it has actually generated: some GPU drivers load the engine
        // and then fail on the first request. Try the preferred accelerator, fall back to CPU.
        val order = if (models.model.preferGpu) listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU) else listOf(LlmInference.Backend.CPU)
        var lastError: Throwable? = null
        for (backend in order) {
            val candidate = runCatching { build(backend) }.getOrElse { lastError = it; continue }
            val works = runCatching { smokeTest(candidate) }.onFailure { lastError = it }.isSuccess
            if (works) {
                engine = candidate
                accelerator = backend.name.lowercase()
                return@withContext
            }
            runCatching { candidate.close() }
        }
        throw IllegalStateException("on-device model failed to run: ${lastError?.message}", lastError)
    }

    /** One short greedy generation; throws if the accelerator can't actually run the model. */
    private fun smokeTest(llm: LlmInference) {
        val session = LlmInferenceSession.createFromOptions(
            llm, LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(1).setTemperature(0f).build(),
        )
        try {
            session.addQueryChunk(models.model.promptFormat.format("Reply with OK.", "Hi"))
            check(session.generateResponse().isNotBlank()) { "empty smoke-test output" }
        } finally {
            session.close()
        }
    }

    /**
     * Streams tokens from a fresh greedy session. When the collector stops early (the engine has
     * a complete JSON frame), generation is cancelled natively so no CPU is wasted on trailing text.
     */
    override fun generate(request: GenerationRequest): Flow<String> = callbackFlow {
        val llm = checkNotNull(engine) { "prepare() not called" }
        val session = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(1).setTemperature(0f).build(),
        )
        session.addQueryChunk(models.model.promptFormat.format(request.systemInstruction, request.prompt))
        session.generateResponseAsync { partial, done ->
            if (!partial.isNullOrEmpty()) trySend(partial)
            if (done) close()
        }
        awaitClose {
            runCatching { session.cancelGenerateResponseAsync() }
            runCatching { session.close() }
        }
    }.flowOn(Dispatchers.Default)

    override fun close() {
        engine?.close()
        engine = null
    }
}

/** Platform recognizer, preferring on-device models (offline-first). */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(languageTag: String): Flow<Transcript> = callbackFlow {
        val listener = object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { trySend(Transcript.Partial(it)) }
            }
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: 0.8f
                trySend(Transcript.Final(text, confidence))
                close()
            }
            override fun onError(error: Int) { close(IllegalStateException("speech error $error")) }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        main.post {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
                startListening(intent)
            }
        }
        awaitClose { main.post { recognizer?.destroy(); recognizer = null } }
    }

    override fun stop() {
        main.post { recognizer?.stopListening() }
    }
}
