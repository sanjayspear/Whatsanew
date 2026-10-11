package dev.pulse.catalog.config

import dev.pulse.catalog.application.port.outbound.IdGenerator
import dev.pulse.catalog.application.port.outbound.ListingEventPublisher
import dev.pulse.catalog.application.port.outbound.ListingRepository
import dev.pulse.catalog.application.port.outbound.TransactionRunner
import dev.pulse.catalog.application.service.CatalogService
import dev.pulse.catalog.domain.ListingId
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** The composition root: the only place that knows which adapter backs which port. */
@Configuration
@EnableScheduling
class CatalogConfiguration {

    private val random = SecureRandom()

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** Time-ordered UUIDv7 keeps B-tree inserts append-mostly at high write rates. */
    @Bean
    fun idGenerator(): IdGenerator = IdGenerator { ListingId(uuidV7()) }

    @Bean
    fun catalogService(
        listings: ListingRepository,
        events: ListingEventPublisher,
        tx: TransactionRunner,
        ids: IdGenerator,
        clock: Clock,
    ) = CatalogService(listings, events, tx, ids, clock)

    private fun uuidV7(): UUID {
        val millis = System.currentTimeMillis()
        val msb = (millis shl 16) or (0x7000L) or (random.nextInt(0x1000).toLong())
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb)
    }
}
