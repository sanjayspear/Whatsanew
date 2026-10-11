# Repository layout

```
pulse/
├── gradle/libs.versions.toml     One version catalog for every Gradle build (single place to bump)
├── gradlew, settings.gradle.kts  One wrapper; root settings is an IDE-only umbrella composite
├── build-logic/                  Convention plugins: pulse.kmp.pure | kmp.library | kmp.compose | spring.service
│
├── packages/                     Shared, platform-agnostic contracts (own Gradle build)
│   ├── ui-dsl/                   KMP (JVM + iOS): IntentFrame, Surface/Node, UiPatch, Theme,
│   │   │                         SurfaceComposer, SurfaceReducer, DslValidator. Used by mobile AND services
│   │   └── examples/*.json       Golden payloads: few-shots, Kotlin golden tests, Python eval set
│   └── event-contracts/          CloudEvents JSON Schemas per topic, versioned in the type (.v1)
│
├── apps/mobile/                  Kotlin Multiplatform (own Gradle build)
│   ├── shared/core/              No UI: AiEngine ladder, rules parser, intent pipeline, resolver,
│   │                             ranker, privacy, signal uploader, model updater, SQLDelight, Ktor
│   │                             expect/actual: androidMain (MediaPipe, Tink, SpeechRecognizer),
│   │                             iosMain (Swift bridges), desktopMain (dev harness)
│   ├── shared/ui/                Compose MP: design tokens, neumorphic/glass, registry, renderer,
│   │                             components, landing + discover hosts. Exports PulseKit.framework
│   ├── androidApp/               Thin Android shell (Activity + Application)
│   ├── iosApp/                   XcodeGen spec + SwiftUI host + Swift bridges (FM, Speech, CryptoKit)
│   └── desktopApp/               Dev window + headless screenshot renderer
│
├── services/                     Spring Boot microservices (own Gradle build, auto-discovered)
│   ├── catalog-service/          ★ end to end: hexagonal, PostGIS, outbox→Kafka, OpenAPI, tests
│   ├── api-gateway/ intent-service/ geo-service/ personalization-service/
│   │   ingestion-service/ model-registry-service/ notification-service/
│   │                             Skeletons: domain + ports + real unit tests + test plan + OpenAPI stub
│   └── <service>/api/openapi.yaml  Contract-first API per service
│
├── ai-server/                    Python: FastAPI (frames, re-rank), eval harness, distillation plan
├── infra/
│   ├── helm/pulse-service/       ONE chart for all services; per-service values files
│   ├── terraform/                AWS ap-south-1 env skeleton (EKS, RDS+PostGIS/pgvector, MSK, Redis, S3)
│   ├── docker/                   Generic multi-stage service image
│   └── local/                    docker-compose: PostGIS, Redpanda (Kafka API), Redis, OTel collector
├── .github/workflows/ci.yml      Path-filtered jobs: packages, services, mobile, android, ios, ai
└── docs/                         This documentation, ADR-style decision log, screenshots
```

## Why each top-level directory

- **`packages/` separate from apps and services.** The DSL is *the* contract between the AI,
  the phone and the server. As a JVM + iOS KMP library it is consumed by mobile (via composite build)
  and by Spring services (intent-service reuses the composer for parity) without duplication.
- **Separate Gradle builds.** Mobile builds never resolve Spring, and backend CI never needs an Android SDK.
  Each build is independently cacheable. The umbrella `settings.gradle.kts` still gives one IDE window.
- **`build-logic/`.** Shared configuration lives in code, not copy-paste. That is what makes 20+
  services and N mobile feature modules cheap and drift-free.
- **`services/` auto-discovery.** Adding a service never touches a shared file, so there are no merge
  conflicts between teams.
- **`ai-server/` in Python.** The ML ecosystem (PEFT, ai-edge-torch, coremltools, llama.cpp
  tooling) is Python-native. It talks to Kotlin only through HTTP/gRPC contracts and the shared golden JSON.
- **`infra/` with one Helm chart.** A new service is a values file. Platform concerns (probes,
  PDB, HPA, mesh, security context) are fixed once.

## Growth path (20+ services, N teams)

New mobile features become `apps/mobile/feature/<name>` modules using `pulse.kmp.compose`, and new
components register in the registry. New services are `services/<name>` with `pulse.spring.service`.
New event types go in `packages/event-contracts/<domain>/<event>.vN.schema.json`. None of these
require refactoring existing modules.
