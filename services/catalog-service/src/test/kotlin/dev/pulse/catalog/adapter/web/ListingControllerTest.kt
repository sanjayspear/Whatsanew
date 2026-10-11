package dev.pulse.catalog.adapter.web

import dev.pulse.catalog.adapter.inbound.web.ListingController
import dev.pulse.catalog.adapter.inbound.web.ProblemHandler
import dev.pulse.catalog.application.service.CatalogService
import dev.pulse.catalog.support.Places
import dev.pulse.catalog.support.TestCatalog
import dev.pulse.catalog.support.draft
import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** Web slice wired to the *real* application service over in-memory adapters: no mocks to drift. */
@WebMvcTest(ListingController::class)
@Import(ProblemHandler::class, ListingControllerTest.Wiring::class)
class ListingControllerTest(@Autowired private val mvc: MockMvc, @Autowired private val service: CatalogService) {

    @TestConfiguration
    class Wiring {
        @Bean
        fun catalogService(): CatalogService = TestCatalog().service
    }

    private val createBody = """
        {
          "title": {"en": "Third Wave Coffee", "kn": "ಥರ್ಡ್ ವೇವ್ ಕಾಫಿ"},
          "category": "food.cafe",
          "location": {"lat": 12.9352, "lng": 77.6245},
          "regionCode": "IN-KA-BLR",
          "timeZone": "Asia/Kolkata",
          "priceLevel": 2,
          "price": {"amountMinor": 35000, "currency": "INR"},
          "phone": "+918041234567"
        }
    """.trimIndent()

    @Test
    fun `POST creates a listing with Location header`() {
        mvc.post("/v1/listings") {
            contentType = MediaType.APPLICATION_JSON
            content = createBody
        }.andExpect {
            status { isCreated() }
            header { string("Location", org.hamcrest.Matchers.startsWith("/v1/listings/")) }
            jsonPath("$.title") { value("Third Wave Coffee") }
            jsonPath("$.titles.kn") { value("ಥರ್ಡ್ ವೇವ್ ಕಾಫಿ") }
            jsonPath("$.price.currency") { value("INR") }
        }
    }

    @Test
    fun `GET resolves title by Accept-Language`() {
        val listing = service.publish(draft("Koshy's"))
        mvc.get("/v1/listings/${listing.id}") { header("Accept-Language", "kn-IN") }
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value(endsWith("(ಕನ್ನಡ)")) }
            }
    }

    @Test
    fun `GET search returns items with distance and cursor`() {
        repeat(3) { service.publish(draft("Cafe $it")) }
        mvc.get("/v1/listings") {
            param("lat", Places.KORAMANGALA.lat.toString())
            param("lng", Places.KORAMANGALA.lng.toString())
            param("radius", "3000")
            param("category", "food.cafe")
            param("limit", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].distanceMeters") { exists() }
            jsonPath("$.nextCursor") { exists() }
        }
    }

    @Test
    fun `invalid domain input becomes 400 problem+json`() {
        mvc.post("/v1/listings") {
            contentType = MediaType.APPLICATION_JSON
            content = createBody.replace("food.cafe", "Not A Category")
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }

    @Test
    fun `bean validation failures become 400`() {
        mvc.post("/v1/listings") {
            contentType = MediaType.APPLICATION_JSON
            content = createBody.replace("\"priceLevel\": 2", "\"priceLevel\": 9")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `unknown listing is 404 and double archive is 409`() {
        mvc.get("/v1/listings/00000000-0000-0000-0000-00000000dead").andExpect { status { isNotFound() } }
        val listing = service.publish(draft())
        mvc.post("/v1/listings/${listing.id}/archive").andExpect { status { isOk() } }
        mvc.post("/v1/listings/${listing.id}/archive").andExpect { status { isConflict() } }
    }
}
