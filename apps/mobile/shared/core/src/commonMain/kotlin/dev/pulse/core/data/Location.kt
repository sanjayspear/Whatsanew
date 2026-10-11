package dev.pulse.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live device location, shared by Android (LocationManager) and iOS (CoreLocation).
 *
 * [location] only changes when the device has moved more than [MovementGate.thresholdMeters],
 * so a person walking around doesn't trigger a re-query every few seconds.
 */
interface LocationTracker {
    val location: StateFlow<GeoCoordinate?>
    /** Starts updates; caller must already hold location permission. Idempotent. */
    fun start()
    fun stop()
}

/** Debounce on distance: forwards a fix only if it moved > [thresholdMeters] from the last one. */
class MovementGate(private val thresholdMeters: Double = 250.0) {
    private val _location = MutableStateFlow<GeoCoordinate?>(null)
    val location: StateFlow<GeoCoordinate?> = _location.asStateFlow()

    /** Returns true when the fix was accepted (first fix, or moved far enough). */
    fun offer(fix: GeoCoordinate): Boolean {
        val last = _location.value
        if (last != null && haversineMeters(last.lat, last.lng, fix.lat, fix.lng) <= thresholdMeters) return false
        _location.value = fix
        return true
    }
}

/** Tracker for platforms without location (desktop harness) or before permission is granted. */
class FixedLocationTracker(fix: GeoCoordinate? = null) : LocationTracker {
    private val gate = MovementGate().apply { fix?.let(::offer) }
    override val location: StateFlow<GeoCoordinate?> = gate.location
    override fun start() = Unit
    override fun stop() = Unit
    fun moveTo(fix: GeoCoordinate) = gate.offer(fix)
}

/** Default anchor until the first real fix arrives (MG Road, central Bengaluru). */
val RegionCentre = GeoCoordinate(12.9756, 77.6050)

fun LocationTracker.asProvider(fallback: GeoCoordinate = RegionCentre) = LocationProvider { location.value ?: fallback }
