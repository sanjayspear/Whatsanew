# Cross-cutting concerns

## Testing

| Layer | Tooling | In repo today |
|---|---|---|
| Unit | kotlin.test / JUnit 5 / AssertJ | DSL (31), mobile core (37), services (43 passing + 10 skipped: Docker ITs and planned placeholders), AI (6) |
| Architecture | ArchUnit | `HexagonalArchitectureTest` |
| Integration | Testcontainers (PostGIS, Kafka, Redis) | `CatalogEndToEndIT`; other services have named `@Disabled` placeholders |
| UI | Compose UI tests (`ui-test`) + headless screenshot baselines | `desktopApp:screenshots` (8 baselines); diffing in CI next |
| Contract | Pact (mobile consumer ↔ gateway provider), OpenAPI per service | OpenAPI done; Pact in P4 |
| Model eval | `pulse_ai.eval` on golden examples + device-farm latency/RAM/thermal | Eval harness + promotion gate done |

## Observability

- **OpenTelemetry end to end.** Services export OTLP via Micrometer Tracing. The mobile app propagates a
  `traceparent` on gateway calls (Ktor plugin, P4), so a tap traces through gateway, catalog and Kafka.
- **Metrics.** Prometheus via Actuator. RED dashboards per service. Outbox lag and relay errors.
- **Model drift.** Dashboards for: frame `source` mix (rules vs LLM), LLM escalation rate, rejected-patch
  rate (validator errors), UNKNOWN rate, refine-after-discover rate (a proxy for misunderstanding),
  per-locale accuracy on sampled eval, and per-model-version crash-free sessions. A spike halts rollout.

## Security and privacy

- Signed model manifests (Ed25519, offline key), per-artifact SHA-256, staged rollout, on-device rollback.
- DPoP-bound short-lived tokens; Play Integrity / App Attest; cert pinning; mTLS inside the mesh.
- PII redaction on-device before anything is stored for upload. Only DP histograms at root-category
  granularity leave the device, and the server re-enforces this (ε cap, key granularity, k ≥ 50 cohorts).
- Containers run non-root with a read-only root filesystem. Secrets via External Secrets.
- India DPDP Act 2023 now; GDPR region isolation in Phase 3 through RegionCode-partitioned data.

## Performance budgets (perf floor: Snapdragon 7-series, 6 GB)

| Budget | Target | Mechanism |
|---|---|---|
| Cold start → landing interactive | < 1.2 s | No model load on start; `warmUp()` after first frame; baseline profiles |
| Intent → first paint (dynamic UI) | **< 300 ms p95** | Rules rung (< 5 ms) + composer (< 1 ms) + local resolve; LLM never on this path |
| On-device LLM refine | < 2.5 s p95, timeout-bounded | Frame-only output (≈50 tokens), INT4, GPU delegate |
| Frame time | 16 ms (60 fps) | Canvas solids ~0.1 ms; no runtime blur on cards; keyed lazy lists |
| App download | < 40 MB | Models are OTA, not bundled; Gemini Nano / Apple FM cost 0 MB |

## Battery and thermal: the fallback ladder

1. **Rules only** (always available, ~0 cost)
2. + on-device LLM, **only if** rules confidence < 0.75 **and** thermal status < MODERATE / not `.serious`
3. Server intent-service when online and the device has no LLM rung
4. Model work (downloads, eval) only on unmetered network + charging; signal uploads batched daily
5. One video decoder max (focused tile); motion scale honours OS reduced-motion and the "quieter" theme
