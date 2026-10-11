package dev.pulse.gateway.domain

/**
 * Token bucket per (principal, route class). Pure and clock-injected, so it is unit-testable.
 * The Redis adapter stores (tokens, lastRefillMs) per key with a Lua script for atomicity.
 */
data class TokenBucket(val capacity: Double, val refillPerSecond: Double, val tokens: Double, val lastRefillMs: Long) {
    fun tryConsume(nowMs: Long, cost: Double = 1.0): Pair<Boolean, TokenBucket> {
        val elapsed = (nowMs - lastRefillMs).coerceAtLeast(0) / 1000.0
        val refilled = (tokens + elapsed * refillPerSecond).coerceAtMost(capacity)
        return if (refilled >= cost) true to copy(tokens = refilled - cost, lastRefillMs = nowMs)
        else false to copy(tokens = refilled, lastRefillMs = nowMs)
    }

    companion object {
        fun full(capacity: Double, refillPerSecond: Double, nowMs: Long) = TokenBucket(capacity, refillPerSecond, capacity, nowMs)
    }
}

/** Outbound port: where buckets live (Redis in prod, in-memory in tests). */
interface BucketStore {
    fun load(key: String): TokenBucket?
    fun save(key: String, bucket: TokenBucket)
}

/** Outbound port: Play Integrity / App Attest verdict for anonymous-first sign-up. */
fun interface AttestationVerifier {
    fun verify(platform: String, token: String, nonce: String): Boolean
}
