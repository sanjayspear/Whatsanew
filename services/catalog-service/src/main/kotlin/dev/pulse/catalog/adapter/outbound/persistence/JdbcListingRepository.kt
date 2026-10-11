package dev.pulse.catalog.adapter.outbound.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.pulse.catalog.application.port.outbound.ListingRepository
import dev.pulse.catalog.domain.CategoryKey
import dev.pulse.catalog.domain.Contact
import dev.pulse.catalog.domain.EventWindow
import dev.pulse.catalog.domain.GeoPoint
import dev.pulse.catalog.domain.Listing
import dev.pulse.catalog.domain.ListingId
import dev.pulse.catalog.domain.ListingStatus
import dev.pulse.catalog.domain.LocalizedText
import dev.pulse.catalog.domain.Media
import dev.pulse.catalog.domain.Money
import dev.pulse.catalog.domain.RegionCode
import dev.pulse.catalog.domain.SearchCriteria
import dev.pulse.catalog.domain.SearchCursor
import dev.pulse.catalog.domain.SearchHit
import dev.pulse.catalog.domain.SearchPage
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * PostgreSQL + PostGIS adapter. `geography(Point,4326)` gives correct great-circle distances
 * anywhere on Earth, so there's no Bangalore-specific projection to undo when going global.
 * `ST_DWithin` uses the GiST index.
 */
@Repository
class JdbcListingRepository(
    private val jdbc: JdbcClient,
    private val json: ObjectMapper,
) : ListingRepository {

    override fun insert(listing: Listing) {
        jdbc.sql(
            """
            INSERT INTO listing (id, title, summary, category, location, region_code, time_zone, address,
                price_level, price_amount_minor, price_currency, starts_at, ends_at, phone_e164, website,
                hero_image_url, video_id, status, first_listed_at, updated_at)
            VALUES (:id, CAST(:title AS jsonb), CAST(:summary AS jsonb), :category,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :region, :tz, :address,
                :priceLevel, :priceMinor, :priceCurrency, :startsAt, :endsAt, :phone, :website,
                :hero, :videoId, :status, :firstListedAt, :updatedAt)
            """.trimIndent(),
        ).params(params(listing)).update()
    }

    override fun update(listing: Listing) {
        val rows = jdbc.sql(
            """
            UPDATE listing SET title = CAST(:title AS jsonb), summary = CAST(:summary AS jsonb),
                category = :category, location = ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                region_code = :region, time_zone = :tz, address = :address, price_level = :priceLevel,
                price_amount_minor = :priceMinor, price_currency = :priceCurrency, starts_at = :startsAt,
                ends_at = :endsAt, phone_e164 = :phone, website = :website, hero_image_url = :hero,
                video_id = :videoId, status = :status, updated_at = :updatedAt
            WHERE id = :id
            """.trimIndent(),
        ).params(params(listing)).update()
        check(rows == 1) { "listing ${listing.id} vanished during update" }
    }

    override fun findById(id: ListingId): Listing? =
        jdbc.sql("SELECT $COLUMNS FROM listing l WHERE id = :id")
            .param("id", id.value)
            .query { rs, _ -> mapListing(rs) }
            .optional()
            .orElse(null)

    override fun search(criteria: SearchCriteria): SearchPage {
        val p = mutableMapOf<String, Any?>(
            "lat" to criteria.center.lat,
            "lng" to criteria.center.lng,
            "radius" to criteria.radiusMeters,
            "limit" to criteria.limit + 1,
        )
        val where = mutableListOf(
            "l.status = 'PUBLISHED'",
            "ST_DWithin(l.location, $CENTER, :radius)",
        )
        if (criteria.categories.isNotEmpty()) {
            where += criteria.categories.mapIndexed { i, c ->
                p["cat$i"] = c.value
                p["catPrefix$i"] = c.value + ".%"
                "l.category = :cat$i OR l.category LIKE :catPrefix$i"
            }.joinToString(" OR ", "(", ")")
        }
        criteria.text?.let { p["q"] = it; where += "l.search_doc @@ websearch_to_tsquery('simple', :q)" }
        // Venues (no event window) always pass; events must overlap the requested window.
        criteria.from?.let { p["from"] = Timestamp.from(it); where += "(l.ends_at IS NULL OR l.ends_at >= :from)" }
        criteria.to?.let { p["to"] = Timestamp.from(it); where += "(l.starts_at IS NULL OR l.starts_at <= :to)" }
        criteria.listedAfter?.let { p["listedAfter"] = Timestamp.from(it); where += "l.first_listed_at >= :listedAfter" }
        criteria.priceLevelMax?.let { p["priceMax"] = it; where += "l.price_level <= :priceMax" }

        val keyset = criteria.after?.let {
            p["cDist"] = it.distanceMeters
            p["cId"] = it.id
            "WHERE (t.distance_m, t.id) > (:cDist, :cId)"
        } ?: ""

        val sql = """
            SELECT * FROM (
                SELECT $COLUMNS, ST_Distance(l.location, $CENTER) AS distance_m
                FROM listing l
                WHERE ${where.joinToString("\n  AND ")}
            ) t
            $keyset
            ORDER BY t.distance_m, t.id
            LIMIT :limit
        """.trimIndent()

        val rows = jdbc.sql(sql).params(p).query { rs, _ -> SearchHit(mapListing(rs), rs.getDouble("distance_m")) }.list()
        val page = rows.take(criteria.limit)
        val next = if (rows.size > criteria.limit) page.last().let { SearchCursor(it.distanceMeters, it.listing.id.value) } else null
        return SearchPage(page, next)
    }

    private fun params(l: Listing): Map<String, Any?> = mapOf(
        "id" to l.id.value,
        "title" to json.writeValueAsString(l.title.values),
        "summary" to l.summary?.let { json.writeValueAsString(it.values) },
        "category" to l.category.value,
        "lat" to l.location.lat,
        "lng" to l.location.lng,
        "region" to l.region.value,
        "tz" to l.timeZone.id,
        "address" to l.address,
        "priceLevel" to l.priceLevel,
        "priceMinor" to l.price?.amountMinor,
        "priceCurrency" to l.price?.currency,
        "startsAt" to l.event?.startsAt?.let(Timestamp::from),
        "endsAt" to l.event?.endsAt?.let(Timestamp::from),
        "phone" to l.contact.phoneE164,
        "website" to l.contact.website,
        "hero" to l.media.heroImageUrl,
        "videoId" to l.media.videoId,
        "status" to l.status.name,
        "firstListedAt" to Timestamp.from(l.firstListedAt),
        "updatedAt" to Timestamp.from(l.updatedAt),
    )

    private fun mapListing(rs: ResultSet): Listing {
        val startsAt = rs.instant("starts_at")
        val endsAt = rs.instant("ends_at")
        val priceMinor = rs.getLong("price_amount_minor").takeUnless { rs.wasNull() }
        return Listing(
            id = ListingId(rs.getObject("id", UUID::class.java)),
            title = LocalizedText(json.readValue(rs.getString("title"))),
            summary = rs.getString("summary")?.let { LocalizedText(json.readValue(it)) },
            category = CategoryKey(rs.getString("category")),
            location = GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")),
            region = RegionCode(rs.getString("region_code")),
            timeZone = ZoneId.of(rs.getString("time_zone")),
            address = rs.getString("address"),
            priceLevel = rs.getInt("price_level").takeUnless { rs.wasNull() },
            price = priceMinor?.let { Money(it, rs.getString("price_currency")) },
            event = if (startsAt != null && endsAt != null) EventWindow(startsAt, endsAt) else null,
            contact = Contact(rs.getString("phone_e164"), rs.getString("website")),
            media = Media(rs.getString("hero_image_url"), rs.getString("video_id")),
            status = ListingStatus.valueOf(rs.getString("status")),
            firstListedAt = rs.instant("first_listed_at")!!,
            updatedAt = rs.instant("updated_at")!!,
        )
    }

    private fun ResultSet.instant(column: String): Instant? = getTimestamp(column)?.toInstant()

    private companion object {
        const val CENTER = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography"
        const val COLUMNS = """
            l.id, l.title, l.summary, l.category, ST_Y(l.location::geometry) AS lat, ST_X(l.location::geometry) AS lng,
            l.region_code, l.time_zone, l.address, l.price_level, l.price_amount_minor, l.price_currency,
            l.starts_at, l.ends_at, l.phone_e164, l.website, l.hero_image_url, l.video_id, l.status,
            l.first_listed_at, l.updated_at
        """
    }
}
