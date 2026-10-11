package dev.pulse.modelregistry.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ModelRegistryServiceTest {
    @Test
    fun `rollout ramps and collapses on bad health`() {
        assertThat(generateSequence(0) { RolloutPolicy.nextStep(it, healthy = true) }.take(5).toList()).containsExactly(0, 1, 5, 25, 100)
        assertThat(RolloutPolicy.nextStep(25, healthy = false)).isEqualTo(0)
    }

    @Test
    fun `models that failed eval are never promoted`() {
        assertThat(RolloutPolicy.canPromote(ModelVersion("pulse-intent", "2026.10.3", 10, 5, evalPassed = false))).isFalse()
    }

    @Test @Disabled("P5: sign with test key, verify with the same canonical payload as mobile ModelManifest")
    fun `manifest signatures verify on device`() = Unit
}
