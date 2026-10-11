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
        setContent { PulseApp(graph, requestMicPermission = ::ensureMicPermission) }
    }
}
