# Open questions for the product owner

These are decisions I made with defaults so work could proceed. Each one is cheap to change now and
expensive later.

1. **Spring Boot 3.x vs 4.x.** The brief says 3.x (implemented: 3.5.16), but Boot 3.5 open-source
   support ended in mid-2026. Move to Boot 4.x now (small diff at this size), or buy commercial 3.5 support?
2. **What is "Legacy"?** I interpreted it as "browse every category, classic style" (it currently issues
   "everything near me"). Is it the old classifieds catalogue (posting ads, sellers), or something else?
3. **User-generated listings?** "Classifieds" usually means users post ads. That adds moderation,
   payments and trust/safety services. Is posting in scope, and in which phase?
4. **Maps.** I picked MapLibre with self-hosted or MapTiler vector tiles. Do you have a provider preference
   or a budget (tiles at 10x users are a real line item)?
5. **Cloud.** I assumed AWS ap-south-1 (Mumbai) because the brief mentions S3. Confirm, or GCP (which
   pairs with Gemini/Vertex)?
6. **Booking.** Is "book" deep-linking to partners (BookMyShow-style), or in-app ticketing with payments?
   This decides whether a payments service is needed.
7. **Inline YouTube autoplay.** YouTube's terms restrict autoplay and overlay UI. Are trailers licensed
   content we host (S3 + HLS), or YouTube embeds with tap-to-play?
8. **Languages at launch.** English plus Kannada assumed. Add Hindi/Tamil/Telugu for Bengaluru's mix in P2?
9. **Minimum devices.** Android minSdk 28 and iOS 17 assumed. Is Android 8 / iOS 16 needed for reach?
10. **Data sourcing.** Where do Phase 1 listings come from (partnerships, scraping with permission,
    manual curation)? This decides the ingestion tooling for catalog-service.
