# Microservices

All services share `pulse.spring.service`: Kotlin, Spring Boot 3.5, virtual threads, Actuator
probes, Micrometer + OTLP tracing, JUnit 5, ArchUnit, MockK and Testcontainers. Layout is
hexagonal: `domain/` (pure Kotlin) → `application/` (use cases + ports) → `adapter/` (web, persistence,
messaging) → `config/` (composition root).

| Service | Responsibility | API | Store | Emits |
|---|---|---|---|---|
| **api-gateway** | Edge: DPoP-bound JWT auth, Play Integrity / App Attest, rate limits (token bucket), REST BFF incl. batch `POST /v1/surface:resolve` | REST (public) | Redis | none |
| **intent-service** | Server-side intent resolution for LLM-less devices; parity + eval of on-device output; reuses `packages/ui-dsl` composer | gRPC + REST | stateless (eval logs → S3) | `intent.resolved.v1` (sampled, redacted) |
| **catalog-service** ★ | Listings: cafés, events, shopping, fitness, hidden gems; geo + filter candidate search | REST [openapi](../services/catalog-service/api/openapi.yaml), gRPC (P4) | PostgreSQL + PostGIS | `catalog.listing.v1`: `listing.published.v1`, `listing.archived.v1` |
| **geo-service** | Radius/polygon, metro-aware isochrones, region gazetteer packs for devices | gRPC | PostGIS | `geo.gazetteer.published.v1` |
| **personalization-service** | Cohort affinities from DP histograms (k-anonymity ≥ 50), item embeddings, optional re-rank | gRPC | PostgreSQL + pgvector, Redis | `personalization.cohort.updated.v1` |
| **ingestion-service** | `POST /v1/signals:batch`: validates ε and granularity, dedupes `batchId`, produces to Kafka | REST via gateway | Redis (idempotency) | `signals.batch.accepted.v1` |
| **model-registry-service** | Signed manifests per channel, staged rollout (1→5→25→100%), delta artifacts | REST via gateway | PostgreSQL, S3 + CDN | `models.version.published.v1` |
| **notification-service** | Push / email / in-app; quiet hours in the recipient's zone; frequency caps | consumes Kafka | PostgreSQL, Redis | `notifications.sent.v1` |

## catalog-service, end to end

```
adapter/inbound/web       ListingController  ─┐  DTOs mirror api/openapi.yaml; RFC 9457 problem+json
application/port/inbound  PublishListing, ArchiveListing, GetListing, SearchListings   (one per use case)
application/service       CatalogService (no Spring imports; TransactionRunner port)
domain                    Listing aggregate, LocalizedText, Money, CategoryKey, RegionCode, EventWindow,
                          SearchCriteria, SearchCursor (keyset)
application/port/outbound ListingRepository, ListingEventPublisher, TransactionRunner, IdGenerator
adapter/outbound          JdbcListingRepository (PostGIS ST_DWithin, tsvector FTS, keyset paging)
                          OutboxEventPublisher (same TX) → OutboxRelay (SKIP LOCKED) → Kafka (CloudEvents)
config                    CatalogConfiguration (composition root, UUIDv7 ids)
```

Tests (31 run here, plus 2 Docker ITs that run in CI):
- `ListingDomainTest`: invariants, locale fallback, category prefix semantics, money, E.164, cursors.
- `CatalogServiceTest`: use cases over in-memory adapters ("new cafés ≤3 km", "theatre this weekend", keyset paging).
- `ListingControllerTest`: `@WebMvcTest` wired to the real service, covering 201/400/404/409 and Accept-Language.
- `HexagonalArchitectureTest`: ArchUnit layer rules.
- `CatalogEndToEndIT`: HTTP → Flyway-migrated PostGIS → outbox row, via Testcontainers (skipped without Docker).

Each other service has a pure domain core with passing tests plus `@Disabled` placeholders naming the
integration test that lands in its phase.
