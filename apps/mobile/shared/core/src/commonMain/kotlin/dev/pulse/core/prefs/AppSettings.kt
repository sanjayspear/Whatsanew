package dev.pulse.core.prefs

import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.GeoCoordinate
import dev.pulse.core.data.SearchDefaults
import dev.pulse.dsl.PulseJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** A place the user picked by hand (location denied, or searching somewhere they are not). */
@Serializable
data class ManualArea(val name: String, val lat: Double, val lng: Double)

@Serializable
data class AppSettings(
    val defaultRadiusKm: Int = SearchDefaults.radiusChoicesKm[2],
    /** When set, "near me" means this area instead of the GPS fix. */
    val manualArea: ManualArea? = null,
)

/** Settings that actually change behaviour today. Applies the default radius to [SearchDefaults]. */
class AppSettingsStore(private val kv: KeyValueStore, private val gazetteer: Gazetteer) {
    private val _settings = MutableStateFlow(read().also(::apply))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setDefaultRadiusKm(km: Int) = save(_settings.value.copy(defaultRadiusKm = km))

    /** Returns false when the area isn't known offline (the caller explains; nothing is guessed). */
    fun setManualArea(name: String?): Boolean {
        if (name.isNullOrBlank()) {
            save(_settings.value.copy(manualArea = null))
            return true
        }
        val place = gazetteer.lookup(name.trim()) ?: gazetteer.findIn(name.trim().lowercase()) ?: return false
        save(_settings.value.copy(manualArea = ManualArea(place.name, place.lat, place.lng)))
        return true
    }

    fun manualCoordinate(): GeoCoordinate? = _settings.value.manualArea?.let { GeoCoordinate(it.lat, it.lng) }

    fun reset() = save(AppSettings())

    private fun save(s: AppSettings) {
        kv.put(KEY, PulseJson.encodeToString(AppSettings.serializer(), s))
        apply(s)
        _settings.value = s
    }

    private fun apply(s: AppSettings) {
        SearchDefaults.radiusMeters = s.defaultRadiusKm * 1_000
    }

    private fun read(): AppSettings = kv.get(KEY)
        ?.let { runCatching { PulseJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()

    private companion object {
        const val KEY = "app.settings"
    }
}
