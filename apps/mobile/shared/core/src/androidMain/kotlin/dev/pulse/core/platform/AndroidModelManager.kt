package dev.pulse.core.platform

import android.content.Context
import dev.pulse.core.model.ModelManager
import dev.pulse.core.model.ModelState
import dev.pulse.core.model.OnDeviceModel
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Resumable (HTTP Range) download into app-specific storage, then SHA-256 verification and an
 * atomic rename. App-specific external storage needs no permission and lets developers side-load a
 * model with `adb push` for testing. The model is public open-source weights, not user data.
 */
class AndroidModelManager(
    private val context: Context,
    override val model: OnDeviceModel,
) : ModelManager {

    private val dir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "models/${model.id}").apply { mkdirs() }
    private val file = File(dir, "model.task")
    private val partial = File(dir, "model.task.part")
    private val lock = Mutex()

    private val _state = MutableStateFlow(if (file.exists()) ModelState.Ready else ModelState.NotInstalled)
    override val state: StateFlow<ModelState> = _state.asStateFlow()

    override fun installedPath(): String? = file.takeIf { it.exists() }?.absolutePath

    override suspend fun download() = lock.withLock {
        if (file.exists()) {
            _state.value = ModelState.Ready
            return@withLock
        }
        withContext(Dispatchers.IO) {
            try {
                fetch()
                _state.value = ModelState.Verifying
                val actual = sha256(partial)
                val pinned = model.sha256
                if (!pinned.startsWith("PENDING") && !actual.equals(pinned, ignoreCase = true)) {
                    partial.delete()
                    _state.value = ModelState.Failed("Downloaded file failed verification. Try again.")
                    return@withContext
                }
                check(partial.renameTo(file)) { "could not install model" }
                _state.value = ModelState.Ready
            } catch (e: Exception) {
                _state.value = ModelState.Failed(e.message ?: "Download failed. Check your connection and try again.")
            }
        }
    }

    private suspend fun fetch() {
        var redirects = 0
        var url = URL(model.url)
        while (true) {
            val have = if (partial.exists()) partial.length() else 0L
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: error("redirect without location")
                url = URL(url, location)
                conn.disconnect()
                check(++redirects <= 5) { "too many redirects" }
                continue
            }
            if (code == 416) { conn.disconnect(); return } // already complete
            check(code == 200 || code == 206) { "server returned $code" }
            val append = code == 206
            val total = if (append) have + conn.contentLengthLong else conn.contentLengthLong
            var done = if (append) have else 0L
            conn.inputStream.use { input ->
                FileOutputStream(partial, append).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (coroutineContext.isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = ModelState.Downloading((done.toDouble() / total).toFloat())
                    }
                }
            }
            conn.disconnect()
            return
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = raf.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    override fun delete() {
        file.delete()
        partial.delete()
        _state.value = ModelState.NotInstalled
    }
}
