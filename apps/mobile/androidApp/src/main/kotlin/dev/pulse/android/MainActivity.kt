package dev.pulse.android

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import java.io.ByteArrayOutputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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

    private var photoResult: CompletableDeferred<ByteArray?>? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        photoResult?.complete(uri?.let(::downscaledJpeg))
    }

    /** System photo picker: no storage permission needed; only the picked image is shared with the app. */
    private suspend fun pickPhoto(): ByteArray? {
        val pending = CompletableDeferred<ByteArray?>().also { photoResult = it }
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        return pending.await()
    }

    /** Profile / listing images are kept small (max 512 px, JPEG 80): they live in the local database. */
    private fun downscaledJpeg(uri: Uri): ByteArray? = runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 512) {
                val f = 512f / longest
                decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }.getOrNull()

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
        graph.photoPicker = ::pickPhoto
        setContent {
            PulseApp(
                graph,
                requestMicPermission = ::ensureMicPermission,
                requestLocationPermission = ::ensureLocationPermission,
                onDarkThemeChanged = { dark ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                },
                locationPermitted = {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
                },
            )
        }
    }
}
