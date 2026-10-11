package dev.pulse.core.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.LocationTracker
import dev.pulse.core.data.MovementGate
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform LocationManager (not Google's FusedLocationProvider) so it works on every Android
 * phone, including devices without Google Play Services. Uses the "fused" provider where the
 * OS has one (Android 12+), otherwise network + GPS.
 */
class AndroidLocationTracker(private val context: Context) : LocationTracker {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val gate = MovementGate(thresholdMeters = 250.0)
    override val location: StateFlow<GeoCoordinate?> = gate.location
    private var started = false

    private val listener = LocationListener { loc: Location -> gate.offer(GeoCoordinate(loc.latitude, loc.longitude)) }

    private fun hasPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (started || !hasPermission()) return
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        // Instant answer from the freshest cached fix, then live updates every >= 250 m.
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { gate.offer(GeoCoordinate(it.latitude, it.longitude)) }
        val primary = providers.firstOrNull() ?: return
        manager.requestLocationUpdates(primary, 30_000L, 250f, listener, Looper.getMainLooper())
        started = true
    }

    override fun stop() {
        if (!started) return
        manager.removeUpdates(listener)
        started = false
    }
}
