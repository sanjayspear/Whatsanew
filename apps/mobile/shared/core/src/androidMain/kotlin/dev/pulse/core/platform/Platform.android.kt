package dev.pulse.core.platform

import android.content.Context
import android.os.Build
import android.os.PowerManager
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.crypto.tink.subtle.Ed25519Verify
import dev.pulse.core.ai.AiEngine
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.ai.SpeechEngine
import dev.pulse.core.ai.ThermalMonitor
import dev.pulse.core.ai.TieredAiEngine
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.LocationTracker
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.model.ModelManager
import dev.pulse.core.model.OnDeviceModel
import dev.pulse.core.model.SignatureVerifier
import java.util.Base64

/** Wraps the Android [Context]; created once from `Application`. */
actual class PlatformContext(val android: Context)

/**
 * Android ladder (same universal model as iOS):
 *  1. Qwen2.5-0.5B (Apache-2.0) via MediaPipe LLM Inference: any arm64 phone with >= 4 GB RAM,
 *     downloaded once on demand.
 *  2. Gemini Nano via AICore: optional accelerator on supported flagships (integration point).
 *  3. Rules: always.
 */
actual fun createPlatformAiEngine(context: PlatformContext, gazetteer: Gazetteer, models: ModelManager): AiEngine {
    val app = context.android.applicationContext
    return TieredAiEngine(
        rules = RuleBasedIntentParser(gazetteer),
        ladder = listOf(
            MediaPipeLlmBackend(app, models),
            GeminiNanoBackend(app),
        ),
        thermal = ThermalMonitor {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        },
    )
}

actual fun createModelManager(context: PlatformContext, model: OnDeviceModel): ModelManager =
    AndroidModelManager(context.android.applicationContext, model)

actual fun createSpeechEngine(context: PlatformContext): SpeechEngine = AndroidSpeechEngine(context.android.applicationContext)

actual fun createSignatureVerifier(context: PlatformContext, publicKeyBase64: String): SignatureVerifier {
    val verifier = Ed25519Verify(Base64.getDecoder().decode(publicKeyBase64))
    return SignatureVerifier { payload, signature ->
        runCatching { verifier.verify(Base64.getDecoder().decode(signature), payload) }.isSuccess
    }
}

actual fun createSqlDriver(context: PlatformContext): SqlDriver =
    AndroidSqliteDriver(PulseDatabase.Schema, context.android.applicationContext, "pulse.db")

actual fun createLocationTracker(context: PlatformContext): LocationTracker =
    AndroidLocationTracker(context.android.applicationContext)

actual fun createHttpClient(): io.ktor.client.HttpClient = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
    install(io.ktor.client.plugins.HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
    }
}
