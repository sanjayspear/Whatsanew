package dev.pulse.core.di

import dev.pulse.core.ai.AiEngine
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.ListingResolver
import dev.pulse.core.data.ListingSource
import dev.pulse.core.data.LocationProvider
import dev.pulse.core.data.SampleCatalog
import dev.pulse.core.pipeline.IntentPipeline
import dev.pulse.core.prefs.InMemoryProfileStore
import dev.pulse.core.prefs.ProfileStore
import dev.pulse.core.signals.InMemorySignalLog
import dev.pulse.core.signals.LocalRanker
import dev.pulse.core.signals.SignalLog
import dev.pulse.dsl.ComponentCatalog
import dev.pulse.dsl.DslValidator
import dev.pulse.dsl.SurfaceComposer
import kotlin.random.Random
import kotlin.time.Clock
import org.koin.dsl.module

/**
 * Core object graph. Platform modules override [AiEngine], [ProfileStore], [SignalLog] and
 * [ListingSource] with real implementations (SQLDelight, platform model ladder). The defaults
 * here are what tests and the desktop harness use.
 */
fun coreModule(
    engine: (Gazetteer) -> AiEngine,
    catalog: ComponentCatalog,
    nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) = module {
    single { Gazetteer.Bangalore }
    single<LocationProvider> { LocationProvider { GeoCoordinate(12.9352, 77.6245) } } // Koramangala until GPS fix
    single<SignalLog> { InMemorySignalLog() }
    single<ProfileStore> { InMemoryProfileStore() }
    single<ListingSource> { val seed = SampleCatalog.listings(nowMs()); ListingSource { seed } }
    single { LocalRanker(get()) }
    single { ListingResolver(get(), get(), get(), get(), nowMs) }
    single { engine(get()) }
    single { SurfaceComposer { "surface-" + Random.nextLong().toULong().toString(36) } }
    single { DslValidator(catalog) }
    single { IntentPipeline(get(), get(), get(), get(), get(), get(), nowMs) }
}
