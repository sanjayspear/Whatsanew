package dev.pulse.geo.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class GeoServiceTest {
    @Test
    fun `geohash matches the reference encoding`() {
        // Reference value from the original geohash.org algorithm.
        assertThat(Geohash.encode(57.64911, 10.40744, 11)).isEqualTo("u4pruydqqvj")
    }

    @Test
    fun `nearby points share a prefix`() {
        assertThat(Geohash.encode(12.9352, 77.6245, 5)).isEqualTo(Geohash.encode(12.9360, 77.6250, 5))
    }

    @Test @Disabled("P4: Testcontainers postgis/postgis, ST_DWithin over seeded Bangalore places")
    fun `radius query returns places ordered by distance`() = Unit
}
