package dev.pulse.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The universal on-device LLM: one open-source model in one runtime (MediaPipe LLM Inference /
 * LiteRT) on both Android and iOS, so every phone gets the same understanding regardless of vendor.
 *
 * Choice: Qwen2.5-0.5B-Instruct (Apache-2.0, fully open source, redistributable). At ~520 MB
 * (8-bit) it fits the 6 GB perf floor with headroom; 1.5B (1.5 GB) does not. Gemma was rejected
 * because its weights use a custom, non-OSI license. Not bundled in the APK/IPA: downloaded once
 * on demand, verified, then hot-swapped into the engine.
 */
data class OnDeviceModel(
    val id: String,
    val displayName: String,
    val url: String,
    /** Hex SHA-256 of the file; a mismatch deletes the download. */
    val sha256: String,
    val sizeBytes: Long,
    val license: String,
    val promptFormat: PromptFormat,
    /** This build's intended accelerator. The 8-bit LiteRT Qwen bundle targets CPU (XNNPACK). */
    val preferGpu: Boolean = false,
) {
    val sizeLabel: String get() = "${sizeBytes / (1024 * 1024)} MB"
}

enum class PromptFormat {
    /** Qwen / ChatML. */
    CHATML,
    GEMMA,
    RAW;

    fun format(system: String, user: String): String = when (this) {
        CHATML -> "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
        GEMMA -> "<start_of_turn>user\n$system\n\n$user<end_of_turn>\n<start_of_turn>model\n"
        RAW -> "$system\n\n$user"
    }
}

object OnDeviceModels {
    val QwenHalfB = OnDeviceModel(
        id = "qwen2.5-0.5b-instruct-q8",
        displayName = "Qwen2.5 0.5B",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        sha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2",
        sizeBytes = 546_660_344,
        license = "Apache-2.0",
        promptFormat = PromptFormat.CHATML,
    )

    /** In production the URL comes from model-registry-service (signed manifest, our CDN). */
    val Default = QwenHalfB
}

sealed interface ModelState {
    data object Unsupported : ModelState
    data object NotInstalled : ModelState
    data class Downloading(val fraction: Float) : ModelState
    data object Verifying : ModelState
    data object Ready : ModelState
    data class Failed(val reason: String) : ModelState
}

/** Downloads, verifies and locates the on-device model. One implementation per platform. */
interface ModelManager {
    val model: OnDeviceModel
    val state: StateFlow<ModelState>
    /** Absolute path of the verified model file, or null when not installed. */
    fun installedPath(): String?
    /** Resumable download + SHA-256 verification. Safe to call repeatedly. */
    suspend fun download()
    fun delete()
}

/** For platforms (desktop harness) that never run an on-device LLM. */
class UnsupportedModelManager(override val model: OnDeviceModel = OnDeviceModels.Default) : ModelManager {
    override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Unsupported)
    override fun installedPath(): String? = null
    override suspend fun download() = Unit
    override fun delete() = Unit
}
