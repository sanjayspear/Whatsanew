package dev.pulse.core.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.pulse.core.ai.AiEngine
import dev.pulse.core.ai.RuleBasedIntentParser
import dev.pulse.core.ai.SpeechEngine
import dev.pulse.core.ai.TieredAiEngine
import dev.pulse.core.data.FixedLocationTracker
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.LocationTracker
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.model.ModelManager
import dev.pulse.core.model.OnDeviceModel
import dev.pulse.core.model.SignatureVerifier
import dev.pulse.core.model.UnsupportedModelManager
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Desktop dev harness. `databaseUrl` defaults to in-memory so screenshot runs are hermetic. */
actual class PlatformContext(val databaseUrl: String = JdbcSqliteDriver.IN_MEMORY)

/** Rules only: the desktop harness exists to iterate on UI and the pipeline, not on models. */
actual fun createPlatformAiEngine(context: PlatformContext, gazetteer: Gazetteer, models: ModelManager): AiEngine =
    TieredAiEngine(RuleBasedIntentParser(gazetteer), ladder = emptyList())

actual fun createModelManager(context: PlatformContext, model: OnDeviceModel): ModelManager = UnsupportedModelManager(model)

actual fun createSpeechEngine(context: PlatformContext): SpeechEngine = NoSpeechEngine

actual fun createSignatureVerifier(context: PlatformContext, publicKeyBase64: String): SignatureVerifier {
    // Wrap the raw 32-byte Ed25519 key in its X.509 SubjectPublicKeyInfo header for the JDK.
    val raw = Base64.getDecoder().decode(publicKeyBase64)
    val spki = ED25519_SPKI_PREFIX + raw
    val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
    return SignatureVerifier { payload, signature ->
        runCatching {
            Signature.getInstance("Ed25519").run {
                initVerify(key)
                update(payload)
                verify(Base64.getDecoder().decode(signature))
            }
        }.getOrDefault(false)
    }
}

actual fun createSqlDriver(context: PlatformContext): SqlDriver =
    JdbcSqliteDriver(context.databaseUrl, schema = PulseDatabase.Schema)

private val ED25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

/** Desktop harness: fixed at Koramangala so screenshots stay deterministic. */
actual fun createLocationTracker(context: PlatformContext): LocationTracker =
    FixedLocationTracker(GeoCoordinate(12.9352, 77.6245))

actual fun createHttpClient(): io.ktor.client.HttpClient = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
    install(io.ktor.client.plugins.HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
    }
}
