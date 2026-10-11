# Pulse: technical change record

**Read this before changing code.** `SESSION-LOG.md` records what was asked and decided; this file
records what changed in the code, why, how the pieces connect, and what's fragile. Newest first.

Baseline: commit `b672faf` on `main` (session 1, 2026-10-11: the original scaffold). Everything
below is session 2 (2026-10-12) and is **not yet committed or pushed**: 74 paths changed locally.

---

## Session 2 at a glance

| # | Area | Outcome | Verified on |
|---|---|---|---|
| 1 | On-device AI (Qwen2.5-0.5B, open source) | Runs on Android CPU via MediaPipe; refines in ~6 s | Pixel 8a |
| 2 | AI grounding (`FrameMerge`) | Model can fill gaps, never override the rules or invent places | Pixel 8a + tests |
| 3 | Keyboard overlap (QA issue 1) | Input stays above the keyboard | Pixel 8a |
| 4 | Live location (QA issue 2) | Real GPS, re-query after > 250 m | Pixel 8a |
| 5 | All-category search (QA issue 2) | Hotels, fashion, events, fitness, essentials… | Pixel 8a + tests |
| 6 | Category picker (QA issue 3) | Shown for vague requests | Pixel 8a + UI tests |
| 7 | Role on first launch (QA issue 4) | Asked once, persisted, changeable in Settings | Pixel 8a + UI tests |
| 8 | Real places (OpenStreetMap) | 21,471 Bengaluru places offline + live elsewhere | Pixel 8a + tests |
| 9 | Small UX fixes | Keyboard hides on search, system Back, status-bar fade, widening note | Pixel 8a |
| 10 | Card buttons Save + Share (user report: "not working") | Save → personal shelf (in Settings), Share → system share sheet | Pixel 8a + 3 UI tests |
| 11 | **DB migration (crash found on device)** | Upgrading over an older install crashed on launch; fixed with `1.sqm` + test | Pixel 8a + test |

Tests at end of session: DSL **34**, mobile core **60**, Compose UI **7**, services **53**, AI server **6**: all passing.
Android release APK: `dist/pulse-0.3.1.apk` (31.6 MB). iOS: shared Kotlin compiles; app not built (no Xcode on this Mac).

---

## 1. On-device AI: universal open-source model

**Why:** the user asked for a model that is open source and works on all Android and iOS phones,
not vendor-only (Gemini Nano / Apple FM). See memory note `universal-open-source-ai`.

**Model:** Qwen2.5-0.5B-Instruct, 8-bit LiteRT `.task` (Apache-2.0), 546,660,344 bytes, SHA-256
`e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2`, downloaded on demand from
Hugging Face (`litert-community`). Gemma rejected (custom licence, gated); 1.5B rejected (1.5 GB).

| File | What it does |
|---|---|
| `shared/core/.../model/OnDeviceModels.kt` | Model catalog (`OnDeviceModel`), `PromptFormat` (ChatML for Qwen), `ModelManager` interface, `ModelState`, `UnsupportedModelManager` (desktop). `preferGpu = false` for this build. |
| `shared/core/src/androidMain/.../AndroidModelManager.kt` | Resumable HTTP-Range download into **app-specific external storage** (`Android/data/dev.pulse.app/files/models/<id>/model.task`, so `adb push` works for testing), SHA-256 verify, atomic rename. |
| `shared/core/src/androidMain/.../AndroidBackends.kt` | `MediaPipeLlmBackend`: per-request greedy `LlmInferenceSession` (topK 1, temp 0), **streams** tokens via `generateResponseAsync`, cancels natively when the collector stops. `prepare()` runs a **smoke-test generation**; GPU is tried only if `preferGpu`, else CPU. Gemini Nano is still a stub rung. |
| `shared/core/src/iosMain/.../Platform.ios.kt`, `NativeBridges.kt` | iOS ladder: MediaPipe (Swift bridge) → Apple FM → rules. `IosModelManager` delegates download to Swift. |
| `apps/mobile/iosApp/iosApp/Bridges/MediaPipeBridge.swift`, `Podfile` | Swift `MediaPipeBridge` (MediaPipeTasksGenAI pod) and `ModelStoreBridge` (URLSession + CryptoKit SHA-256). **Never compiled** (no Xcode). |
| `shared/core/.../ai/TieredAiEngine.kt` | `refresh()` (hot-swap after download); **8 s** LLM budget (was 2.5 s); early stop on first complete JSON (`takeWhile`); records `EngineStatus.lastLlm` (timeout / unparseable / no new information / refined in N ms, with first-token time). |
| `shared/core/.../ai/AiEngine.kt` | `refresh()` added to the interface; `EngineStatus.lastLlm`. |
| `shared/ui/.../screens/DiscoverScreen.kt` | `AiModelChip` ("Add on-device AI · 521 MB", progress, retry). Diagnostics line shows `AI: …` and `Data: …`. |

**Findings on the Pixel 8a (Tensor G3):**
- The GPU path **loads but fails at the first generation** ("Failed to upload data to GPU … Invalid command queue"). Hence the smoke test and CPU default. Don't re-enable GPU without re-testing.
- CPU: about 6 s per refinement with the current prompt (system + 3 few-shots). Rules still paint first (under 10 ms).
- The 0.5B model **copies its few-shot examples** ("cafés in Koramangala within 3 km"); see §2.

## 2. AI grounding: `FrameMerge` and when the LLM runs

**Why:** on the device, "what's new near me" was rewritten by the model into "cafés in Koramangala",
which made the app look food-only and stuck in one place.

| File | What it does |
|---|---|
| `shared/core/.../ai/FrameMerge.kt` | **The LLM may fill gaps, never contradict.** Rules' slots (place, time, freshness, price, openNow, task for commands) win. LLM categories only if the rules found none. LLM places are accepted **only if the user actually said them** (`RuleBasedIntentParser.mentionsPlace`). |
| `shared/core/.../ai/RuleBasedIntentParser.kt` | Confidence becomes 0.9 when **every content word is explained** (`KNOWN_TERMS`), so fully-understood requests never wake the LLM. |

Rule of thumb: the LLM is only for words the rules can't explain ("somewhere quiet to read").

## 3. Keyboard overlap (QA issue 1)

**Root cause:** edge-to-edge (`enableEdgeToEdge`) makes Android ignore `adjustResize`; the bar only padded
for the navigation bar.

| File | What it does |
|---|---|
| `shared/ui/.../design/Insets.kt` | `Modifier.keyboardSafe()` = `WindowInsets.ime ∪ navigationBars` (bottom). Works on iOS too. |
| `shared/ui/.../design/PulseTextField.kt` | The single shared text input; hides the keyboard on submit. Reused by the intent bar and the picker's "Other" field. |
| `StructuralComponents.kt` (`LayoutStackComponent`) | List bottom padding includes `WindowInsets.ime`, so focused fields scroll above the keyboard. |
| `PulseApp.kt` | `BackHandler` (compose `ui-backhandler`): system Back on Discover/Settings returns to Landing. |
| `DiscoverScreen.kt` | Gradient scrim behind the status bar + back button so scrolled content doesn't collide. |

## 4. Live location (QA issue 2)

| File | What it does |
|---|---|
| `shared/core/.../data/Location.kt` | `LocationTracker` interface, `MovementGate` (only emits after > 250 m), `FixedLocationTracker` (desktop/tests), `RegionCentre`, `asProvider()`. |
| `shared/core/src/androidMain/.../AndroidLocationTracker.kt` | Platform `LocationManager` (fused on Android 12+, else network/GPS). **Not** Play Services, so it works on every phone. |
| `shared/core/src/iosMain/.../IosLocationTracker.kt` | CoreLocation from Kotlin (`CLLocationManagerDelegateProtocol`), distance filter 250 m. Compiles; not run on a device. |
| `MainActivity.kt`, `AndroidManifest.xml` | Location permission request (fine + coarse), shown the first time Discover opens. |
| `iosApp/project.yml` | `NSLocationWhenInUseUsageDescription`. |
| `RenderScope.dataVersion` + `remember(bind, scope.dataVersion)` | Location/sync changes force data-bound components to re-resolve (no stale cache). |

## 5. Search coverage and parsing

| File | What it does |
|---|---|
| `shared/core/.../ai/Lexicon.kt` | `CategoryHit` now holds **multiple categories** (`EVENTS = events, arts, music, comedy`). New words: fashion/clothes/sarees/sneakers…, events/workshops/festivals, food/lunch/bakery, malls/electronics, parks/lakes/museums, **hotels/lodges/hostels/stays**, pharmacy/hospital/ATM/fuel. **"gym(s)" maps to `fitness`** (OSM splits gyms across fitness and sports centres). Note: in Indian English "hotel" can mean an eatery; we read it as lodging. |
| `RuleBasedIntentParser.kt` | Strips the matched place name before category matching (so "Cubbon Park" isn't the park category). Captures **unknown places** after in/near/around/at for online lookup. Location words ("nearby") are never search text. |
| `shared/core/.../data/CategoryChoices.kt` | The picker's options (Food, Fashion, Fitness, Events, Shopping). |
| `SampleCatalog.kt` | +23 fictional listings (incl. north-west Bengaluru). **Now only used by the desktop harness and tests** (`useSampleData = true`). Devices use real data. |

## 6. Category picker (QA issue 3)

| File | What it does |
|---|---|
| `packages/ui-dsl/.../SurfaceComposer.kt` | `ListingQuery.isAmbiguous()`; ambiguous surfaces get an `intent.category_picker` node on top (role `picker`), regardless of layout prefs. **No DSL schema change**: new component key + `Roles.PICKER` string constant only. |
| `shared/ui/.../components/CategoryPicker.kt` | Registered component; choice → `session.discover(query.copy(categories))` + REFINE signal; "Other" → shared text field → full intent pipeline. |
| `shared/ui/.../registry/ComponentRegistry.kt` | `SessionActions` (discover, setRole, role) passed via `RenderScope.session`. |

## 7. Role on first launch (QA issue 4)

| File | What it does |
|---|---|
| `shared/core/.../prefs/UserRole.kt` | `UserRole`, `UserRoleRepository`, `SqlUserRoleRepository` (kv key `user.role`; SQLDelight, so no expect/actual), in-memory variant. |
| `shared/ui/.../components/RolePicker.kt` | `onboarding.role_picker` (mode onboarding / settings). |
| `shared/ui/.../registry/RoleGate.kt` | `RoleGate` + `RoleExperience.forRole()`: **merchant UI later = change one line there.** `LocalUserRole`. |
| `shared/ui/.../screens/SurfaceHost.kt` | `SurfaceHost` + `AppSurfaces.onboarding()/settings()`: app screens are DSL surfaces too. |
| `LandingScreen.kt`, `PulseIcons.kt` | Settings button (sliders icon). |

## 8. Real places: OpenStreetMap

**Decision:** free + open source (user's requirement) → OpenStreetMap (ODbL). Attribution
"© OpenStreetMap contributors" is shown under OSM results and **must stay**.

**Data flow on a device:**
```
query → ListingResolver.resolveWidening → SqlListingCache.near(bbox, indexed)
            ↑ filled by                         ↑ filled by
   RegionPacks (bundled Bengaluru pack)     NearbySync → OverpassClient (live, only OUTSIDE packs)
                                                       → PlaceGeocoder (Nominatim) for unknown areas
```

| File | What it does |
|---|---|
| `shared/core/.../data/BengaluruPlaces.kt` | **Generated** 1,086 OSM neighbourhoods + short-form aliases ("rr nagar", "bsk", "kr puram"…). Merged into `Gazetteer.Bangalore` after hand-curated landmarks. |
| `shared/core/.../data/osm/OsmCategories.kt` | **Single source of truth** mapping Pulse categories ↔ OSM tags; `categoryOf(tags)`. |
| `shared/core/.../data/osm/OverpassClient.kt` | Live client: **exact-tag clauses** (`nw["amenity"="theatre"](around:…)`; regex versions timed out), retry once on 429/504, 3 mirrors, identifying User-Agent. `parse()` also reads region packs (same JSON shape). "New" = version-1 elements' creation timestamp. |
| `shared/core/.../data/osm/PlaceGeocoder.kt` | Nominatim lookup for areas outside the pack; cached per name. |
| `shared/core/.../data/NearbySync.kt` | Fetches for the bindings on screen; 6 h freshness per (~1 km cell, radius ≤ 10 km, categories); **skips live fetch inside packs** (`coveredOffline`); `syncing` true while queued; user-facing `lastError` + technical `lastErrorDetail`. |
| `shared/core/.../data/RegionPacks.kt` | Loads bundled packs into the cache on first launch (skips if already loaded). `AVAILABLE` = Bengaluru bbox. |
| `shared/ui/src/commonMain/composeResources/files/regions/IN-KA-BLR.json` | **The Bengaluru pack: 21,471 places**, 4.3 MB (about 1.5 MB in the APK). Read via `Res.readBytes` (compose resources, `dev.pulse.ui.res`). |
| `shared/core/.../data/SqlStores.kt`, `Pulse.sq` | `SqlListingCache` is region-agnostic, has an **indexed bbox query** (`listingsInBox`, index on lat/lng), eviction, `countInRegion`. |
| `shared/core/.../data/ListingResolver.kt` | `ListingSource.near()`; `resolveWidening()` widens ×2.5 up to 10 km when < 3 results → `Resolution(items, requested, radius, withinRequested)`; geocoded fallback for unknown places. |
| `shared/ui/.../PulseApp.kt` (`PulseGraph.create`) | `useSampleData` flag (desktop/tests true; devices false); builds cache, packs, sync, geocoder, HTTP client; `DataStatus` (loading / notice) to components. |
| `StructuralComponents.kt`, `MapStack.kt` | Use `Resolution`: widened note ("Only N places within 2 km, so here are more within 5 km"), map ring shows the effective radius, OSM attribution line, "Looking around for places…". |
| `shared/core/.../platform/Platform.kt` (+ actuals) | `expect fun createHttpClient()` (OkHttp / Darwin) and `createLocationTracker()`. |
| `tools/osm-pack/build_region_pack.py` | Reusable pack builder (pyosmium; bbox; node locations on disk). Regenerate: see "Regenerating data". |

**Scaling plan (agreed, not built):** no per-city packs. catalog-service imports OSM (India → world)
weekly into PostGIS and serves ~5 km tiles; the phone fetches tiles on demand into a capped LRU cache
(20–50 MB) with an optional "save city offline". `NearbySync`/`RegionPacks` are the swap points.

## 8b. Card actions: Save and Share

**User report:** on a place card only "Go" did anything; the other two buttons did nothing.
**Root cause:** Save/Share were mapped to `pulse://save|share/<id>` URIs, which the Android launcher
deliberately ignores ("handled in-app"), but nothing in the app handled them. There was no shelf and no share code.

| File | What it does |
|---|---|
| `shared/core/.../prefs/SavedShelf.kt` | `SavedShelf` / `SqlSavedShelf`: saved places as full JSON (offline, survives cache eviction); `ids` StateFlow; `toggle()`. |
| `Pulse.sq` | `saved_listing` table + queries. |
| `shared/ui/.../ActionLauncher.kt` (+ android/ios/desktop actuals) | `share(subject, text)`: Android chooser, iOS `UIActivityViewController`, desktop clipboard. `shareText()` = name, address, phone, OpenStreetMap link. SAVE/SHARE no longer produce URIs. |
| `PulseApp.kt` (`PulseGraph.dispatch`) | SAVE toggles the shelf + SAVE signal + toast; SHARE opens the share sheet. `messages` flow drives toasts. |
| `components/ActionSheet.kt` | `isActive` state: saved bookmark stays debossed with accent icon; accessibility labels "Save" / "Remove from saved" / "Share" / "Directions". |
| `design/Toast.kt` | `PulseToast` ("Saved to your shelf"), keyboard-safe. |
| `components/SavedShelf.kt`, `SurfaceHost.kt` | `shelf.saved` registered component; Settings surface now shows role + saved places. |

## 8c. Database migrations (must read before touching `Pulse.sq`)

**Found on the Pixel:** installing this build over the previous one crashed on launch with
"no such table: saved_listing". SQLDelight only creates tables on a *fresh* install; existing installs
need a migration file. Fixed with `shared/core/src/commonMain/sqldelight/dev/pulse/core/db/1.sqm`
(adds `saved_listing` and the lat/lng index; schema version is now **2**), plus
`SchemaMigrationTest` (desktopTest), which builds a v1 database and upgrades it with data preserved.

**Rule:** every change to `Pulse.sq` needs a new `N.sqm` (next number) and the migration test extended.

## 9. Build and tooling changes

- `gradle/libs.versions.toml`: `haze` 1.7.3 → **1.6.10** (1.7.x is built with Kotlin 2.3; our K/N 2.2 can't read it); added `compose-ui-backhandler`, `compose-components-resources`.
- `build-logic`: dropped `iosX64` (Haze has no Intel-simulator build).
- `shared/core/build.gradle.kts`: `ktor-client-core` is now `api` (core exposes `HttpClient`).
- `shared/ui/build.gradle.kts`: `desktopTest` uses `compose.desktop.currentOs`; `compose.resources { packageOfResClass = "dev.pulse.ui.res" }`.
- `.gitignore`: `bin/` (IDE output that appeared in `build-logic/` and services; not ours).
- `shared/core/src/desktopTest/.../live/LiveOsmProbe.kt`: **opt-in** live check (`PULSE_LIVE=1`), no-op otherwise.

---

## Invariants: keep these true

1. **Every surface goes through `DslValidator` + atomic `SurfaceReducer`**, whatever produced it.
2. **The LLM never contradicts the rules and never introduces places the user didn't say** (`FrameMerge`).
3. **The UI renders from the local cache only**; network only fills the cache.
4. **New UI = a registered component**, not a hardcoded screen (picker, role, settings follow this).
5. **Don't change the UI Intent DSL schema without asking the user** (their explicit rule).
6. **OSM attribution stays visible** wherever OSM data is shown.
7. **Never regenerate the Android release keystore** (`apps/mobile/androidApp/pulse-release.jks`).
8. Category mapping lives only in `OsmCategories.kt` (the Python pack tool keeps a superset list).
9. **Every `Pulse.sq` change ships with a migration (`N.sqm`) and a migration test** (see §8c).
10. Card actions that aren't OS links (Save, Share, Book) are handled in `PulseGraph.dispatch`, never as unhandled `pulse://` URIs.

## Known issues / tech debt

- Header chip shows the **requested** radius (≤ 2 km) when results widened; the note explains it.
- OSM cards often lack ratings, hours, photos and phones (only Go/Save/Share).
- Events have **no dates** (venues only); needs merchant posting or partner feeds.
- Qwen copies few-shots; contained by `FrameMerge`, not fixed (better few-shots or a LoRA fine-tune).
- Gemini Nano rung is a stub. iOS app never built or run. In-app model download never run on a device (the model was side-loaded).
- Live Overpass is rate-limited / overloaded at times; fine only as a fallback outside packs.
- `SqlListingCache` caps queries at 5,000 rows per bbox.
- "Book" still produces an unhandled `pulse://book/<id>` (only shown for event listings; needs a booking flow).
- Gradle **configuration cache can go stale** after building different included builds back to back
  (`BuildFusService` error). Fix: re-run with `--no-configuration-cache`.

## How to verify

```bash
./gradlew -p packages :ui-dsl:jvmTest
./gradlew -p apps/mobile :shared:core:desktopTest :shared:ui:desktopTest -Ppulse.android=false
./gradlew -p apps/mobile :shared:ui:compileKotlinIosSimulatorArm64 -Ppulse.android=false
./gradlew -p apps/mobile :androidApp:assembleRelease          # needs apps/mobile/local.properties sdk.dir
./gradlew -p services test
./gradlew -p apps/mobile :desktopApp:screenshots -Ppulse.android=false   # docs/screenshots
PULSE_LIVE=1 ./gradlew -p apps/mobile :shared:core:desktopTest --tests "dev.pulse.core.live.LiveOsmProbe" -Ppulse.android=false -i
```

## Device testing notes (Pixel 8a)

- `adb` is at `~/Library/Android/sdk/platform-tools/adb` (not on PATH).
- The phone charges on **AC**: use `adb shell svc power stayon true` while testing, then **`svc power stayon false`** (default was off).
- Side-load the model: `adb push <model.task> /sdcard/Android/data/dev.pulse.app/files/models/qwen2.5-0.5b-instruct-q8/model.task`. `pm clear dev.pulse.app` deletes it.
- `adb shell input text` drops characters on long strings: type in short chunks.
- Location and mic permissions are the user's to grant on screen; don't tap them programmatically.

## Regenerating data

- **Region pack:** download `https://download.geofabrik.de/asia/india/southern-zone-latest.osm.pbf` (verify `.md5`), then
  `python tools/osm-pack/build_region_pack.py <pbf> --bbox 12.75,77.35,13.20,77.85 --region IN-KA-BLR --out IN-KA-BLR.json`
  and copy to `shared/ui/src/commonMain/composeResources/files/regions/`. Bump `RegionPacks` logic if a reload is needed (it skips regions that already have > 1,000 cached rows).
- **Neighbourhood pack (`BengaluruPlaces.kt`):** generated from an Overpass query for `place=suburb|neighbourhood|quarter` in the Bengaluru bbox (script inline in session 2; move it to `tools/` when next regenerated).
