package dev.pulse.core.prefs

import dev.pulse.core.data.ListingItem
import dev.pulse.core.db.PulseDatabase
import dev.pulse.dsl.PulseJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The personal shelf of saved places (the brief's "save to a personal shelf"). Offline, on-device. */
interface SavedShelf {
    val ids: StateFlow<Set<String>>
    /** Saves or removes [item]; returns true if it is now saved. */
    fun toggle(item: ListingItem): Boolean
    fun all(): List<ListingItem>
}

class SqlSavedShelf(private val db: PulseDatabase, private val nowMs: () -> Long) : SavedShelf {
    private val _ids = MutableStateFlow(db.pulseQueries.savedIds().executeAsList().toSet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    override fun toggle(item: ListingItem): Boolean {
        val nowSaved = item.id !in _ids.value
        if (nowSaved) db.pulseQueries.saveListing(item.id, PulseJson.encodeToString(ListingItem.serializer(), item), nowMs())
        else db.pulseQueries.unsaveListing(item.id)
        _ids.value = if (nowSaved) _ids.value + item.id else _ids.value - item.id
        return nowSaved
    }

    override fun all(): List<ListingItem> = db.pulseQueries.savedListings().executeAsList()
        .mapNotNull { runCatching { PulseJson.decodeFromString(ListingItem.serializer(), it) }.getOrNull() }
}

class InMemorySavedShelf : SavedShelf {
    private val items = linkedMapOf<String, ListingItem>()
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()
    override fun toggle(item: ListingItem): Boolean {
        val nowSaved = items.remove(item.id) == null
        if (nowSaved) items[item.id] = item
        _ids.value = items.keys.toSet()
        return nowSaved
    }
    override fun all() = items.values.reversed()
}
