package dev.pulse.ingestion.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class IngestionServiceTest {
    private val published = mutableListOf<SignalBatch>()
    private val ids = mutableSetOf<String>()
    private val accept = AcceptBatch({ ids.add(it) }, { published += it })
    private val batch = SignalBatch("b1", "blr-25-34", "2026.10.3", 0, 1_000, mapOf("arts" to 3.2), 2.0)

    @Test
    fun `accepts once and dedupes retries`() {
        assertThat(accept.accept(batch, 2_000)).isEqualTo(Verdict.Accepted)
        assertThat(accept.accept(batch, 2_000)).isEqualTo(Verdict.Duplicate)
        assertThat(published).hasSize(1)
    }

    @Test
    fun `rejects fine-grained categories that could identify listings`() {
        assertThat(accept.accept(batch.copy(batchId = "b2", histogram = mapOf("arts.theatre.yayati" to 1.0)), 2_000))
            .isInstanceOf(Verdict.Rejected::class.java)
    }

    @Test
    fun `rejects weak privacy budgets`() {
        assertThat(accept.accept(batch.copy(batchId = "b3", epsilon = 50.0), 2_000)).isInstanceOf(Verdict.Rejected::class.java)
    }

    @Test @Disabled("P4: Testcontainers Kafka + Redis, end-to-end POST /v1/signals:batch")
    fun `batch lands on Kafka exactly once`() = Unit
}
