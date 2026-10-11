package dev.pulse.core.signals

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/** Wire format for `POST /v1/signals:batch` (ingestion-service via api-gateway). */
@Serializable
data class SignalBatch(
    val batchId: String,
    val deviceCohort: String,
    val modelVersion: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val histogram: Map<String, Double>,
    val epsilon: Double,
)

/**
 * Batched, privatized, retried upload. It is scheduled by the platform (WorkManager on Android,
 * BGTaskScheduler on iOS) on unmetered network + charging when possible. The request body is
 * gzip-compressed by Ktor ContentEncoding and protected by TLS (+ cert pinning at gateway).
 * [SignalBatch.batchId] is an idempotency key, so retries never double count.
 */
class SignalUploader(
    private val log: SignalLog,
    private val http: HttpClient,
    private val endpoint: String,
    private val ldp: LocalDifferentialPrivacy = LocalDifferentialPrivacy(),
    private val cohort: () -> String,
    private val modelVersion: () -> String,
    private val newBatchId: () -> String,
) {
    suspend fun uploadOnce(maxAttempts: Int = 4): Result<Int> {
        val pending = log.pendingUpload(limit = 500)
        if (pending.isEmpty()) return Result.success(0)
        val signals = pending.map { it.second }
        val batch = SignalBatch(
            batchId = newBatchId(),
            deviceCohort = cohort(),
            modelVersion = modelVersion(),
            windowStartMs = signals.minOf { it.atEpochMs },
            windowEndMs = signals.maxOf { it.atEpochMs },
            histogram = ldp.privatize(LocalDifferentialPrivacy.histogramOf(signals)),
            epsilon = 2.0,
        )
        var backoffMs = 1_000L
        repeat(maxAttempts) { attempt ->
            try {
                val response = http.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(batch)
                }
                if (response.status.isSuccess()) {
                    log.markUploaded(pending.map { it.first })
                    return Result.success(pending.size)
                }
                if (response.status.value in 400..499 && response.status.value != 429) {
                    return Result.failure(IllegalStateException("rejected: ${response.status}"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // network error: retry below
            }
            if (attempt < maxAttempts - 1) {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(60_000)
            }
        }
        return Result.failure(IllegalStateException("upload failed after $maxAttempts attempts"))
    }
}
