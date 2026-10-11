# Phased delivery plan

Status as of 2026-10-11: **P0-P2 core is implemented and running on desktop (tests and screenshots);
Android and iOS shells are written but not yet built on a machine with the Android SDK / Xcode.**

| Phase | Scope | Exit criteria | Demo script | Key risks | Status |
|---|---|---|---|---|---|
| **P0** | Repo, CI, KMP hello on both platforms | `ci.yml` green on all jobs; `androidApp` installs; iOS app launches via XcodeGen | Open app on Pixel + iPhone, see landing | Toolchain drift (AGP/Kotlin/CMP matrix) | Repo + CI written; **Android/iOS builds unverified locally** |
| **P1** | Static registry + 3D landing buttons + MapLibre + inline video | 60 fps landing on SD7 floor (Macrobenchmark); MapLibre offline pack for BLR; focused-tile autoplay | Rotate device, open "What's New", map tiles appear offline | Canvas 3D jank on low-end GPUs; YouTube ToS for inline autoplay | Registry, 3D buttons, schematic map, video chrome **done** |
| **P2** | On-device intent → DSL → dynamic render (text) | Golden set ≥95% task / 90% slot accuracy; first paint p95 < 300 ms on floor; LLM refine p95 < 2.5 s | Type the 5 example queries; theme + layout persist across surfaces | Rules coverage for Kanglish/Hinglish; LLM RAM on 6 GB | Rules + pipeline + composer **done**; MediaPipe rung written; Gemini Nano is an integration point |
| **P3** | Voice, camera, video modalities | Offline STT WER < 15% (en-IN); vision tag → category ≥ 85% on test set | Speak in Kannada-English; photograph a dosa; 5 s video of a venue | On-device STT for Kannada; camera permission UX | Speech engines written (Android/iOS); vision is an interface only |
| **P4** | Microservices + ingestion + personalization loop | catalog/geo/ingestion/personalization in staging; Pact contracts green; DP uploads flowing; ranker A/B shows +CTR | Book a play → next day theatre rises in "What's New" | Cold start of cohorts (k ≥ 50); Kafka ops | catalog **done**, others skeleton + domain tests |
| **P5** | Distillation pipeline + OTA + global scale-out | Signed delta OTA to 1% → 100% with auto-rollback; second region live with RegionCode partitioning | Ship model v2 mid-demo; device hot-swaps; Berlin data seeded | Model regressions in the long tail; data residency (DPDP, GDPR) | Pipeline plan, eval gate, ModelUpdater, RolloutPolicy **done** |
