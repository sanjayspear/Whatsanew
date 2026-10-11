package dev.pulse.core.ai

import kotlinx.coroutines.flow.Flow

/**
 * On-device speech-to-text. Platform recognizers (Android SpeechRecognizer with
 * EXTRA_PREFER_OFFLINE, iOS SFSpeechRecognizer with requiresOnDeviceRecognition) are tried
 * first, with whisper.cpp tiny/base as the offline fallback where they are unavailable.
 */
interface SpeechEngine {
    val isAvailable: Boolean
    fun listen(languageTag: String): Flow<Transcript>
    fun stop()
}

sealed interface Transcript {
    val text: String
    data class Partial(override val text: String) : Transcript
    data class Final(override val text: String, val confidence: Float) : Transcript
}

/** On-device vision: object labels + OCR text from a camera frame (MediaPipe Tasks). */
interface VisionAnalyzer {
    suspend fun analyze(frame: ImageFrame): List<String>
}

class ImageFrame(val width: Int, val height: Int, val rgba: ByteArray)
