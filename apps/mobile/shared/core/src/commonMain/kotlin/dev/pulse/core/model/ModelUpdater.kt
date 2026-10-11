package dev.pulse.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Platform file + download plumbing, kept behind a port so the update logic is testable. */
interface ModelStore {
    fun installedVersion(modelId: String): String?
    suspend fun download(artifact: ModelManifest.Artifact, into: String): String
    fun sha256(path: String): String
    /** Atomic rename of staged dir to active, keeping the previous version for rollback. */
    fun activate(modelId: String, version: String, stagedDir: String)
    fun rollback(modelId: String)
}

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Installed(val version: String) : UpdateResult
    data class Rejected(val reason: String) : UpdateResult
}

/**
 * Pull -> verify signature -> verify hashes -> stage -> atomic activate -> hot-swap.
 * The engine reads [activeVersion] and reloads the backend between requests, so a new model takes
 * effect on the next intent without an app restart. A post-swap smoke test failure rolls back.
 */
class ModelUpdater(
    private val store: ModelStore,
    private val verifier: SignatureVerifier,
    private val appVersion: Int,
    private val deviceBucket: Int, // stable 0..99 hash of install id, for staged rollout
    private val smokeTest: suspend (version: String) -> Boolean,
) {
    private val _activeVersion = MutableStateFlow<String?>(null)
    val activeVersion: StateFlow<String?> = _activeVersion.asStateFlow()

    suspend fun apply(manifest: ModelManifest): UpdateResult {
        if (!verifier.verify(manifest.canonicalPayload(), manifest.signature)) return UpdateResult.Rejected("bad signature")
        if (manifest.minAppVersion > appVersion) return UpdateResult.Rejected("requires app ${manifest.minAppVersion}")
        if (deviceBucket >= manifest.rolloutPercent) return UpdateResult.UpToDate
        if (store.installedVersion(manifest.modelId) == manifest.version) return UpdateResult.UpToDate

        val staged = "staging/${manifest.modelId}/${manifest.version}"
        for (artifact in manifest.artifacts) {
            val path = store.download(artifact, staged)
            if (!store.sha256(path).equals(artifact.sha256, ignoreCase = true)) {
                return UpdateResult.Rejected("hash mismatch for ${artifact.name}")
            }
        }
        store.activate(manifest.modelId, manifest.version, staged)
        if (!smokeTest(manifest.version)) {
            store.rollback(manifest.modelId)
            return UpdateResult.Rejected("smoke test failed; rolled back")
        }
        _activeVersion.value = manifest.version
        return UpdateResult.Installed(manifest.version)
    }
}
