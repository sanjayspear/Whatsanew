package dev.pulse.core.model

import kotlinx.serialization.Serializable

/**
 * Served by model-registry-service at `GET /v1/models/{channel}/manifest`. The manifest is
 * signed (Ed25519) with an offline release key whose public half is compiled into the app.
 * A compromised CDN or registry therefore can't push a model.
 */
@Serializable
data class ModelManifest(
    val modelId: String,              // "pulse-intent"
    val version: String,              // "2026.10.3"
    val channel: String,              // "stable" | "beta"
    val format: Format,
    val minAppVersion: Int,
    val artifacts: List<Artifact>,
    val rolloutPercent: Int = 100,
    val signature: String = "",       // base64 Ed25519 over canonicalPayload()
) {
    @Serializable
    enum class Format { TFLITE_TASK, GGUF, COREML, LEXICON_PACK }

    @Serializable
    data class Artifact(
        val name: String,
        val url: String,
        val sha256: String,
        val sizeBytes: Long,
        /** If present, a bsdiff patch from [deltaFromVersion] (typically 5-15% of full size). */
        val deltaFromVersion: String? = null,
    )

    /** Deterministic bytes that are signed: everything except the signature itself. */
    fun canonicalPayload(): ByteArray = buildString {
        append(modelId).append('|').append(version).append('|').append(channel).append('|')
        append(format.name).append('|').append(minAppVersion).append('|').append(rolloutPercent)
        artifacts.sortedBy { it.name }.forEach { a ->
            append('|').append(a.name).append(':').append(a.sha256).append(':').append(a.sizeBytes).append(':').append(a.deltaFromVersion ?: "-")
        }
    }.encodeToByteArray()
}

/** Ed25519 verification: JDK on desktop, Tink on Android, CryptoKit (via Swift bridge) on iOS. */
fun interface SignatureVerifier {
    fun verify(payload: ByteArray, signatureBase64: String): Boolean
}
