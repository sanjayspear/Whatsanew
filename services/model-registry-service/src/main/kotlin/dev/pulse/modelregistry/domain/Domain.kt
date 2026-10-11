package dev.pulse.modelregistry.domain

data class ModelVersion(val modelId: String, val version: String, val minAppVersion: Int, val rolloutPercent: Int, val evalPassed: Boolean)

/**
 * Gate for promoting a distilled model. Mirrors the client check (ModelUpdater), so the server
 * never advertises a version a device would reject.
 */
object RolloutPolicy {
    fun canPromote(candidate: ModelVersion): Boolean = candidate.evalPassed && candidate.rolloutPercent in 0..100

    /** Ramp 1% -> 5% -> 25% -> 100%, advancing only while crash-free and drift metrics hold. */
    fun nextStep(current: Int, healthy: Boolean): Int = when {
        !healthy -> 0
        current < 1 -> 1
        current < 5 -> 5
        current < 25 -> 25
        else -> 100
    }
}

/** Outbound port: signs manifests with the offline release key (HSM/KMS-backed in prod). */
fun interface ManifestSigner {
    fun sign(canonicalPayload: ByteArray): String
}
