package dev.pulse.core.prefs

import dev.pulse.dsl.PulseJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class HistoryEntry(val text: String, val atEpochMs: Long)

/**
 * Recent searches, newest first, on this device only. Can be switched off in Settings; switching it
 * off also forgets what was stored. Clearing *history* is separate from clearing the current search.
 */
class SearchHistory(private val kv: KeyValueStore, private val nowMs: () -> Long, private val max: Int = 50) {
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    var enabled: Boolean
        get() = kv.get(ENABLED) != "false"
        set(value) {
            kv.put(ENABLED, value.toString())
            if (!value) clear()
        }

    fun record(text: String) {
        val t = text.trim()
        if (!enabled || t.length < 2) return
        write(listOf(HistoryEntry(t, nowMs())) + _entries.value.filterNot { it.text.equals(t, ignoreCase = true) })
    }

    fun remove(text: String) = write(_entries.value.filterNot { it.text == text })

    fun clear() = write(emptyList())

    private fun write(list: List<HistoryEntry>) {
        val capped = list.take(max)
        kv.put(KEY, PulseJson.encodeToString(ListSerializer(HistoryEntry.serializer()), capped))
        _entries.value = capped
    }

    private fun read(): List<HistoryEntry> = kv.get(KEY)
        ?.let { runCatching { PulseJson.decodeFromString(ListSerializer(HistoryEntry.serializer()), it) }.getOrNull() }
        .orEmpty()

    private companion object {
        const val KEY = "search.history"
        const val ENABLED = "search.history.enabled"
    }
}
