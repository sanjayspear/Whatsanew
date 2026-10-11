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
}
