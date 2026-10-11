package dev.pulse.core.ai

import kotlinx.coroutines.flow.Flow

/**
 * One rung on the on-device model ladder: Gemini Nano (AICore), Apple Foundation Models,
 * Gemma/Phi INT4 via MediaPipe, or llama.cpp. Text in, streamed text out. Everything model-
 * specific (prompt format, constrained decoding) stays behind this interface.
 */
interface LanguageModelBackend {
    val id: String
    suspend fun availability(): BackendAvailability
    suspend fun prepare() {}
    fun generate(request: GenerationRequest): Flow<String>
    fun close() {}
}

data class GenerationRequest(
    val systemInstruction: String,
    val prompt: String,
    val maxOutputTokens: Int = 96,
    val temperature: Float = 0f,
    /** JSON schema for backends with constrained/guided decoding (llama.cpp GBNF, Apple @Generable). */
    val jsonSchema: String? = null,
)

enum class BackendAvailability {
    AVAILABLE,
    /** Supported but weights not on device yet (AICore download, OTA model pull). */
    DOWNLOADABLE,
    UNAVAILABLE,
}
