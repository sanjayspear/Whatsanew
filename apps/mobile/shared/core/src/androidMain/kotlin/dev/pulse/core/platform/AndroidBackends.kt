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
import dev.pulse.core.ai.BackendAvailability
import dev.pulse.core.ai.GenerationRequest
import dev.pulse.core.ai.LanguageModelBackend
import dev.pulse.core.ai.SpeechEngine
import dev.pulse.core.ai.Transcript
import java.io.File
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

/** Gemma INT4 `.task` bundle via MediaPipe LLM Inference (GPU delegate where supported). */
class MediaPipeLlmBackend(
    private val context: Context,
    private val modelFile: () -> File,
) : LanguageModelBackend {
    override val id = "mediapipe-gemma-int4"
    private var engine: LlmInference? = null

    override suspend fun availability(): BackendAvailability {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val enoughRam = mem.totalMem >= 5_500_000_000L && !am.isLowRamDevice // 6 GB perf floor
        return when {
            !enoughRam -> BackendAvailability.UNAVAILABLE
            modelFile().exists() -> BackendAvailability.AVAILABLE
            else -> BackendAvailability.DOWNLOADABLE
        }
    }

    override suspend fun prepare() = withContext(Dispatchers.Default) {
        if (engine == null) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile().absolutePath)
                .setMaxTokens(512)
                .build()
            engine = LlmInference.createFromOptions(context, options)
        }
    }

    override fun generate(request: GenerationRequest): Flow<String> = flow {
        val llm = checkNotNull(engine) { "prepare() not called" }
        emit(llm.generateResponse(request.systemInstruction + "\n\n" + request.prompt))
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
