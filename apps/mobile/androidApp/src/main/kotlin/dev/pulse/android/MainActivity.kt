package dev.pulse.android

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.pulse.core.platform.PlatformContext
import dev.pulse.ui.PulseApp
import dev.pulse.ui.PulseGraph
import kotlinx.coroutines.CompletableDeferred

class PulseApplication : Application() {
    /** One graph per process; the AI engine warms up lazily on first composition. */
    val graph: PulseGraph by lazy { PulseGraph.create(PlatformContext(this)) }
}

class MainActivity : ComponentActivity() {
    private var micResult: CompletableDeferred<Boolean>? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micResult?.complete(granted)
    }

    private var locationResult: CompletableDeferred<Boolean>? = null
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        locationResult?.complete(grants.values.any { it })
    }

    /** Fine or approximate both work; "approximate" still re-queries as the user moves. */
    private suspend fun ensureLocationPermission(): Boolean {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) return true
        val pending = CompletableDeferred<Boolean>().also { locationResult = it }
        locationPermission.launch(perms)
        return pending.await()
    }

    /** Asks only when needed; the system remembers "allow" and "don't allow" afterwards. */
    private suspend fun ensureMicPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        val pending = CompletableDeferred<Boolean>().also { micResult = it }
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        return pending.await()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as PulseApplication).graph
        setContent {
            PulseApp(graph, requestMicPermission = ::ensureMicPermission, requestLocationPermission = ::ensureLocationPermission)
        }
    }
}
