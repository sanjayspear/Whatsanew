package dev.pulse.core.prefs

import dev.pulse.dsl.PulseJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * The person using the app. Only a display name is required; everything else is optional.
 * Stored on this device only (there is no account server yet), so the phone number is kept
 * unverified and is never sent anywhere.
 */
@Serializable
data class UserProfile(
    val name: String,
    /** One of [Avatars.ids]; ignored when [hasPhoto]. */
    val avatarId: String = Avatars.ids.first(),
    val hasPhoto: Boolean = false,
    val phone: String? = null,
    val phoneVerified: Boolean = false,
)

object Avatars {
    /** Built-in avatars: a glyph on a colour, drawn by the UI (no bundled images). */
    val ids = listOf("spark", "sun", "leaf", "wave", "star", "moon")
}

/** Validation shared by onboarding and profile editing. Returns a user-facing problem, or null. */
object ProfileRules {
    fun nameProblem(name: String): String? = when {
        name.isBlank() -> "Please enter your name"
        name.trim().length > 40 -> "Keep it under 40 characters"
        else -> null
    }

    /** Optional. Accepts Indian mobile numbers with or without +91 / spaces; normalized to +91XXXXXXXXXX. */
    fun normalizePhone(raw: String): Result<String?> {
        val digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) return Result.success(null)
        val local = digits.removePrefix("+91").removePrefix("0").let { if (it.length == 12 && it.startsWith("91")) it.drop(2) else it }
        return if (local.length == 10 && local.all(Char::isDigit) && local[0] in '6'..'9') Result.success("+91$local")
        else Result.failure(IllegalArgumentException("Enter a 10-digit mobile number"))
    }
}

class ProfileRepository(private val kv: KeyValueStore) {
    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    fun save(profile: UserProfile) {
        kv.put(KEY, PulseJson.encodeToString(UserProfile.serializer(), profile))
        _profile.value = profile
    }

    /** Profile photo as small JPEG/PNG bytes (downscaled by the platform picker), base64 in the kv table. */
    fun photo(): ByteArray? = kv.get(PHOTO)?.let { runCatching { kotlin.io.encoding.Base64.decode(it) }.getOrNull() }

    fun savePhoto(bytes: ByteArray?) {
        if (bytes == null) kv.remove(PHOTO) else kv.put(PHOTO, kotlin.io.encoding.Base64.encode(bytes))
        _profile.value?.let { save(it.copy(hasPhoto = bytes != null)) }
    }

    fun clear() {
        kv.remove(KEY)
        kv.remove(PHOTO)
        _profile.value = null
    }

    private fun read(): UserProfile? = kv.get(KEY)?.let { runCatching { PulseJson.decodeFromString(UserProfile.serializer(), it) }.getOrNull() }

    private companion object {
        const val KEY = "user.profile"
        const val PHOTO = "user.photo"
    }
}
