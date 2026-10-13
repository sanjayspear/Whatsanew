package dev.pulse.core.prefs

import dev.pulse.core.db.PulseDatabase

/**
 * Small string settings and documents (profile, history, merchant posts), stored in the existing `kv`
 * table, so new features need no schema migration. Values are JSON where structured.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun removePrefix(prefix: String)
}

class SqlKeyValueStore(private val db: PulseDatabase) : KeyValueStore {
    override fun get(key: String) = db.pulseQueries.getValue(key).executeAsOneOrNull()
    override fun put(key: String, value: String) { db.pulseQueries.putValue(key, value) }
    override fun remove(key: String) { db.pulseQueries.deleteValue(key) }
    override fun removePrefix(prefix: String) { db.pulseQueries.deleteValuesWithPrefix(prefix, prefix + '\uFFFF') }
}

class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun removePrefix(prefix: String) { map.keys.removeAll { it.startsWith(prefix) } }
}
