package dev.pulse.core

import dev.pulse.core.model.ModelManifest
import dev.pulse.core.model.ModelStore
import dev.pulse.core.model.ModelUpdater
import dev.pulse.core.model.UpdateResult
import dev.pulse.core.signals.LocalDifferentialPrivacy
import dev.pulse.core.signals.PiiRedactor
import dev.pulse.core.signals.Signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PrivacyAndModelUpdateTest {

    @Test
    fun piiIsRedactedBeforeUpload() {
        val redacted = PiiRedactor.redact("call me on +91 98450 12345 or mail asha@example.com")
        assertEquals("call me on [phone] or mail [email]", redacted)
    }

    @Test
    fun histogramsAreCoarseAndNoisy() {
        val signals = listOf(
            Signal(Signal.Kind.ATTEND, "arts.theatre", "secret-listing", 0),
            Signal(Signal.Kind.TAP, "arts.cinema", "secret-listing-2", 0),
        )
        val histogram = LocalDifferentialPrivacy.histogramOf(signals)
        assertEquals(setOf("arts"), histogram.keys, "only root categories leave the device")
        val noisy = LocalDifferentialPrivacy(random = Random(42)).privatize(histogram)
        assertTrue(noisy.getValue("arts") != histogram.getValue("arts"))
    }

    private val manifest = ModelManifest(
        modelId = "pulse-intent", version = "2026.10.3", channel = "stable", format = ModelManifest.Format.TFLITE_TASK,
        minAppVersion = 10, artifacts = listOf(ModelManifest.Artifact("model.task", "https://cdn/x", "abc123", 600_000_000)),
        rolloutPercent = 50, signature = "sig",
    )

    private class FakeStore(var hash: String = "abc123") : ModelStore {
        var active: String? = null
        var rolledBack = false
        override fun installedVersion(modelId: String) = active
        override suspend fun download(artifact: ModelManifest.Artifact, into: String) = "$into/${artifact.name}"
        override fun sha256(path: String) = hash
        override fun activate(modelId: String, version: String, stagedDir: String) { active = version }
        override fun rollback(modelId: String) { rolledBack = true; active = null }
    }

    private fun updater(store: ModelStore, validSig: Boolean = true, bucket: Int = 10, smoke: Boolean = true) =
        ModelUpdater(store, { _, _ -> validSig }, appVersion = 12, deviceBucket = bucket, smokeTest = { smoke })

    @Test
    fun validManifestInstallsAndHotSwaps() = runTest {
        val u = updater(FakeStore())
        assertEquals(UpdateResult.Installed("2026.10.3"), u.apply(manifest))
        assertEquals("2026.10.3", u.activeVersion.value)
    }

    @Test
    fun badSignatureIsRejectedBeforeAnyDownload() = runTest {
        assertEquals(UpdateResult.Rejected("bad signature"), updater(FakeStore(), validSig = false).apply(manifest))
    }

    @Test
    fun tamperedArtifactIsRejected() = runTest {
        assertIs<UpdateResult.Rejected>(updater(FakeStore(hash = "evil")).apply(manifest))
    }

    @Test
    fun devicesOutsideRolloutStayPut() = runTest {
        assertEquals(UpdateResult.UpToDate, updater(FakeStore(), bucket = 80).apply(manifest))
    }

    @Test
    fun failedSmokeTestRollsBack() = runTest {
        val store = FakeStore()
        assertIs<UpdateResult.Rejected>(updater(store, smoke = false).apply(manifest))
        assertTrue(store.rolledBack)
    }

    @Test
    fun canonicalPayloadIsOrderIndependentForArtifacts() {
        val a = ModelManifest.Artifact("a", "u", "h1", 1)
        val b = ModelManifest.Artifact("b", "u", "h2", 2)
        assertTrue(manifest.copy(artifacts = listOf(a, b)).canonicalPayload().contentEquals(manifest.copy(artifacts = listOf(b, a)).canonicalPayload()))
    }
}
