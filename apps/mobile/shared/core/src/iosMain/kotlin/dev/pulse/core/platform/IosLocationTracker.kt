package dev.pulse.core.platform

import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.LocationTracker
import dev.pulse.core.data.MovementGate
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.StateFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.darwin.NSObject

/** CoreLocation, called directly from Kotlin (no Swift bridge needed: it's an Objective-C API). */
@OptIn(ExperimentalForeignApi::class)
class IosLocationTracker : LocationTracker {
    private val gate = MovementGate(thresholdMeters = 250.0)
    override val location: StateFlow<GeoCoordinate?> = gate.location

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            (didUpdateLocations.lastOrNull() as? CLLocation)?.coordinate?.useContents {
                gate.offer(GeoCoordinate(latitude, longitude))
            }
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            manager.startUpdatingLocation()
        }
    }

    private val manager = CLLocationManager().apply {
        desiredAccuracy = kCLLocationAccuracyHundredMeters
        distanceFilter = 250.0
        delegate = this@IosLocationTracker.delegate
    }

    /** Also triggers the iOS permission prompt (NSLocationWhenInUseUsageDescription) on first call. */
    override fun start() {
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    override fun stop() = manager.stopUpdatingLocation()
}
