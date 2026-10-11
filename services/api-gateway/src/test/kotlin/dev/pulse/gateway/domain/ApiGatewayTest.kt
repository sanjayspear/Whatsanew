package dev.pulse.gateway.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ApiGatewayTest {
    @Nested
    inner class RateLimiting {
        @Test
        fun `bucket drains then refills over time`() {
            var b = TokenBucket.full(capacity = 2.0, refillPerSecond = 1.0, nowMs = 0)
            repeat(2) { val (ok, next) = b.tryConsume(0); assertThat(ok).isTrue(); b = next }
            assertThat(b.tryConsume(0).first).isFalse()
            assertThat(b.tryConsume(1_000).first).isTrue()
        }
    }

    @Nested
    inner class Contract {
        @Test @Disabled("P4: Pact provider verification against apps/mobile consumer pacts")
        fun `BFF satisfies mobile consumer contract`() = Unit

        @Test @Disabled("P4: Testcontainers Redis + WireMock upstreams")
        fun `routes v1 listings to catalog-service with auth`() = Unit
    }
}
