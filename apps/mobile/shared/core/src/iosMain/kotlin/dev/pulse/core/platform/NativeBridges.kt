package dev.pulse.core.platform

/**
 * Implemented in Swift (see `apps/mobile/iosApp/iosApp/Bridges/`) and passed into
 * [PlatformContext] at app start. Kotlin interfaces are exported to Swift as protocols.
 */
interface NativeLlmBridge {
    /** e.g. `SystemLanguageModel.default.availability == .available` */
    fun isAvailable(): Boolean
    fun isThermallyThrottled(): Boolean
    /** Calls back exactly once with (text, null) or (null, error). */
    fun generate(systemInstruction: String, prompt: String, onResult: (String?, String?) -> Unit)
}

interface NativeSpeechBridge {
    fun isAvailable(): Boolean
    fun start(languageTag: String, onPartial: (String) -> Unit, onFinal: (String, Float) -> Unit, onError: (String) -> Unit)
    fun stop()
}

interface NativeCryptoBridge {
    fun verifyEd25519(payloadBase64: String, signatureBase64: String, publicKeyBase64: String): Boolean
}

/** MediaPipe LLM Inference (MediaPipeTasksGenAI pod) running the universal model. Implemented in Swift. */
interface NativeMediaPipeBridge {
    fun load(modelPath: String): Boolean
    fun generate(prompt: String, onResult: (String?, String?) -> Unit)
    fun close()
}

/** URLSession download + CryptoKit SHA-256 verification into Application Support. Implemented in Swift. */
interface NativeModelBridge {
    fun installedPath(modelId: String): String?
    fun download(modelId: String, url: String, sha256: String, onProgress: (Double) -> Unit, onDone: (String?, String?) -> Unit)
    fun delete(modelId: String)
}
