package dev.pulse.notification.domain

import java.time.Instant
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class NotificationServiceTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test
    fun `11pm in Bangalore is quiet, 7pm is not`() {
        assertThat(QuietHours().isQuiet(Instant.parse("2026-02-14T17:30:00Z"), ist)).isTrue()   // 23:00 IST
        assertThat(QuietHours().isQuiet(Instant.parse("2026-02-14T13:30:00Z"), ist)).isFalse()  // 19:00 IST
    }

    @Test @Disabled("P4: consume personalization.cohort.updated.v1 from Testcontainers Kafka and fan out")
    fun `high-affinity events trigger one push within caps`() = Unit
}
