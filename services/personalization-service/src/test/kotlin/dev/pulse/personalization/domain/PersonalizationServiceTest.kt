package dev.pulse.personalization.domain

import kotlin.random.Random
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class PersonalizationServiceTest {
    @Test
    fun `noisy uploads converge to the true cohort mean`() {
        val rnd = Random(7)
        val agg = CohortAggregator(minDevices = 50)
        repeat(5_000) { agg.add(mapOf("arts" to 4.0 + (rnd.nextDouble() - 0.5) * 10)) }
        assertThat(agg.affinities()!!.getValue("arts")).isCloseTo(4.0, within(0.2))
    }

    @Test
    fun `small cohorts publish nothing`() {
        val agg = CohortAggregator(minDevices = 50)
        repeat(10) { agg.add(mapOf("arts" to 1.0)) }
        assertThat(agg.affinities()).isNull()
    }

    @Test @Disabled("P4: Testcontainers pgvector, kNN over item embeddings")
    fun `similar items to a liked play are other plays first`() = Unit
}
