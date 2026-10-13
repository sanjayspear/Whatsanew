package dev.pulse.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.pulse.core.data.SqlListingCache
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.prefs.SqlSavedShelf
import dev.pulse.core.data.SampleCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Upgrading an installed app must not crash. Found on a Pixel 8a: adding `saved_listing` without a
 * migration crashed every existing install on launch ("no such table"). This test builds a database
 * exactly as version 1 of the app created it, then upgrades it.
 */
class SchemaMigrationTest {

    private val v1Schema = listOf(
        "CREATE TABLE listing_cache (id TEXT NOT NULL PRIMARY KEY, region_code TEXT NOT NULL, category TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, json TEXT NOT NULL, fetched_at INTEGER NOT NULL)",
        "CREATE INDEX listing_cache_region ON listing_cache(region_code, category)",
        "CREATE TABLE signal (id INTEGER PRIMARY KEY AUTOINCREMENT, json TEXT NOT NULL, at_epoch_ms INTEGER NOT NULL, uploaded INTEGER NOT NULL DEFAULT 0)",
        "CREATE INDEX signal_time ON signal(at_epoch_ms)",
        "CREATE TABLE kv (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
    )

    @Test
    fun `version 1 database upgrades to the current schema and keeps its data`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        v1Schema.forEach { driver.execute(null, it, 0) }
        driver.execute(null, "INSERT INTO kv(key, value) VALUES ('user.role', 'REGULAR')", 0)

        PulseDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = PulseDatabase.Schema.version)
        val db = PulseDatabase(driver)

        assertTrue(PulseDatabase.Schema.version >= 2)
        assertEquals("REGULAR", db.pulseQueries.getValue("user.role").executeAsOne(), "existing data survives")
        val shelf = SqlSavedShelf(db) { 0L }
        val item = SampleCatalog.listings(0).first()
        assertTrue(shelf.toggle(item), "new table works after upgrade")
        SqlListingCache(db).upsert(listOf(item), 0)
        assertEquals(1, SqlListingCache(db).near(dev.pulse.core.data.GeoCoordinate(item.lat, item.lng), 500).size)
    }

    @Test
    fun `version 2 rows get searchable names after the upgrade`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        v1Schema.forEach { driver.execute(null, it, 0) }
        PulseDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)
        // A row written by version 2 of the app: no search_text column yet.
        val item = SampleCatalog.listings(0).first()
        val json = dev.pulse.dsl.PulseJson.encodeToString(dev.pulse.core.data.ListingItem.serializer(), item).replace("'", "''")
        driver.execute(null, "INSERT INTO listing_cache VALUES ('${item.id}', 'IN', '${item.category}', ${item.lat}, ${item.lng}, '$json', 0)", 0)

        PulseDatabase.Schema.migrate(driver, oldVersion = 2, newVersion = PulseDatabase.Schema.version)
        val cache = SqlListingCache(PulseDatabase(driver))
        val here = dev.pulse.core.data.GeoCoordinate(item.lat, item.lng)
        val byName: (String, String) -> Boolean = { _, text -> dev.pulse.core.data.NameMatch.score(text, item.title("en")) > 0 }
        assertTrue(cache.nearMatching(here, 500, 10, byName).isEmpty(), "not searchable until backfilled")
        cache.backfillSearchText()
        assertEquals(listOf(item.id), cache.nearMatching(here, 500, 10, byName).map { it.id })
    }

    @Test
    fun `dense areas are not truncated`() {
        val db = PulseDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { PulseDatabase.Schema.create(it) })
        val cache = SqlListingCache(db)
        val base = SampleCatalog.listings(0).first()
        // 6,000 cafés plus one office, all within a few hundred metres: the office must still be found by name.
        val many = (0 until 6_000).map { i -> base.copy(id = "c$i", titles = mapOf("en" to "Cafe $i"), category = "food.cafe", lat = base.lat + (i % 80) * 1e-5, lng = base.lng + (i / 80) * 1e-5) }
        val office = base.copy(id = "ms", titles = mapOf("en" to "Microsoft"), category = "business.office")
        cache.upsert(many + office, 0)
        val found = cache.nearMatching(dev.pulse.core.data.GeoCoordinate(base.lat, base.lng), 6_000, 50) { _, t -> dev.pulse.core.data.NameMatch.score(t, "microsoft") > 0 }
        assertEquals(listOf("ms"), found.map { it.id })
    }
}
