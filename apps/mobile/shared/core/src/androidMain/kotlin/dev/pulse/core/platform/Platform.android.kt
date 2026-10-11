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
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.model.SignatureVerifier
import java.io.File
import java.util.Base64

/** Wraps the Android [Context]; created once from `Application`. */
actual class PlatformContext(val android: Context)

/**
 * Android ladder:
 *  1. Gemini Nano via AICore: zero app-size cost, on Pixel 8+/Galaxy S24+ class devices.
 *  2. Gemma 3 1B / Gemma 2B INT4 via MediaPipe LLM Inference: OTA-delivered, any device with >= 6 GB RAM.
 *  3. Rules: always.
 */
actual fun createPlatformAiEngine(context: PlatformContext, gazetteer: Gazetteer): AiEngine {
    val app = context.android.applicationContext
    return TieredAiEngine(
        rules = RuleBasedIntentParser(gazetteer),
        ladder = listOf(
            GeminiNanoBackend(app),
            MediaPipeLlmBackend(app) { File(app.filesDir, "models/pulse-intent/active/model.task") },
        ),
        thermal = ThermalMonitor {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        },
    )
}

actual fun createSpeechEngine(context: PlatformContext): SpeechEngine = AndroidSpeechEngine(context.android.applicationContext)

actual fun createSignatureVerifier(context: PlatformContext, publicKeyBase64: String): SignatureVerifier {
    val verifier = Ed25519Verify(Base64.getDecoder().decode(publicKeyBase64))
    return SignatureVerifier { payload, signature ->
        runCatching { verifier.verify(Base64.getDecoder().decode(signature), payload) }.isSuccess
    }
}

actual fun createSqlDriver(context: PlatformContext): SqlDriver =
    AndroidSqliteDriver(PulseDatabase.Schema, context.android.applicationContext, "pulse.db")
