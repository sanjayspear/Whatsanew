package dev.pulse.catalog.adapter.inbound.web

import dev.pulse.catalog.application.port.inbound.ArchiveListing
import dev.pulse.catalog.application.port.inbound.GetListing
import dev.pulse.catalog.application.port.inbound.PublishListing
import dev.pulse.catalog.application.port.inbound.SearchListings
import dev.pulse.catalog.application.port.inbound.SearchQuery
import dev.pulse.catalog.domain.GeoPoint
import dev.pulse.catalog.domain.ListingId
import jakarta.validation.Valid
import java.net.URI
import java.time.Instant
import java.util.Locale
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/listings")
class ListingController(
    private val publishListing: PublishListing,
    private val archiveListing: ArchiveListing,
    private val getListing: GetListing,
    private val searchListings: SearchListings,
) {

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateListingRequest,
        @RequestHeader("Accept-Language", required = false) acceptLanguage: Locale?,
    ): ResponseEntity<ListingResponse> {
        val listing = publishListing.publish(request.toDraft())
        return ResponseEntity.created(URI.create("/v1/listings/${listing.id}"))
            .body(ListingResponse.from(listing, acceptLanguage?.toLanguageTag()))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestHeader("Accept-Language", required = false) acceptLanguage: Locale?,
    ): ListingResponse = ListingResponse.from(getListing.get(ListingId.parse(id)), acceptLanguage?.toLanguageTag())

    @PostMapping("/{id}/archive")
    fun archive(@PathVariable id: String): ListingResponse =
        ListingResponse.from(archiveListing.archive(ListingId.parse(id)), null)

    /** Collection search: `GET /v1/listings?lat=12.93&lng=77.62&radius=3000&category=food.cafe&freshness=NEW` */
    @GetMapping
    fun search(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(required = false) radius: Int?,
        @RequestParam(name = "category", required = false) categories: List<String>?,
        @RequestParam(name = "q", required = false) text: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) freshness: SearchQuery.Freshness?,
        @RequestParam(required = false) priceLevelMax: Int?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) cursor: String?,
        @RequestHeader("Accept-Language", required = false) acceptLanguage: Locale?,
    ): SearchResponse {
        val page = searchListings.search(
            SearchQuery(
                center = GeoPoint(lat, lng),
                radiusMeters = radius,
                categories = categories.orEmpty(),
                text = text,
                from = from,
                to = to,
                freshness = freshness,
                priceLevelMax = priceLevelMax,
                limit = limit,
                cursor = cursor,
            ),
        )
        val language = acceptLanguage?.toLanguageTag()
        return SearchResponse(page.hits.map { ListingResponse.from(it, language) }, page.next?.encode())
    }
}
