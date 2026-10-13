# Pulse: session log

## Session 3, Wednesday 2026-10-14

| # | You asked | What happened |
|---|---|---|
| 1 | "What's this project about?" | Summarised Pulse from the README, docs and memory. |
| 2 | Implement `~/Documents/whatsNew_Req/Search_Around_Functional_Requirements.pdf` (7 pages) | Asked three scoping questions; you declined them. |
| 3 | "Do what is possible now" + "test on the connected Android device" | Built everything that runs on the phone (below); deferred what needs a server. **Tested on the Pixel 8a**; 8 device-only bugs found and fixed. APK: `dist/pulse-0.4.0.apk`. |

### Done (on-device)
- **Rebrand:** app name "Search Around" (package id unchanged, so updates install over Pulse). Home: tagline "One Search. Endless Possibilities.", doors **Search Around** (search existing) and **Search Recently Launched** (new launches).
- **Search:** default radius **6 km** (Settings: 2–25 km); **never widens silently**: "Only N within 6 km. Search further or try another area?" with buttons and an area field.
- **Accuracy:** temples, churches, mosques, gurudwaras, Jain and Buddhist temples, salons, spas, juice shops, IT parks, groceries, schools, colleges, police, post offices and more. A temple search returns temples, never "Temple Meals" (type comes from OSM tags, not names). Typos ("temle", "resturant", "chruch") are corrected conservatively. Exact names rank first. Rebuilt offline pack: **28,236 places** (989 temples, 240 churches, 144 mosques, 79 IT parks).
- **Clarifying questions** for ambiguous requests ("hotels": stay or eat? "shops": what kind?).
- **Clear search** (X in the search bar), separate from clearing history. **Autocomplete** (history, categories, real nearby names).
- **Book Now** only when a real destination exists (cinemas, plays, concerts, comedy, events, or a merchant's link). A booking page lists **BookMyShow** and **District by Zomato** (Paytm Insider redirects to District) plus the venue or organiser's site; it keeps what you searched. Verified on the Pixel: BookMyShow's **installed app opened** at Now Showing (Bengaluru); plays and events pages valid.
- **Account setup** on first launch (name required; avatar or photo; optional phone, stored unverified; User or Merchant). **Profile**, **Sign out**, **Delete my data**.
- **Hamburger menu:** Profile, Search Around, Search Recently Launched, Search History, Saved Items, (merchant: Publish an offering, My posts), Settings, Help and Support, About, Privacy Policy and Terms, Sign Out.
- **Merchant publishing** with on-device validation and moderation (required fields, https-only links, dates, duplicates, blocked phrases). Posts appear in search and in Recently Launched on that phone.
- **Settings** (only what works): location / chosen area, default radius, Light / Dark / Black / Warm, text size, reduce motion, history on/off, account, version. **About**, **Help** (FAQ + report a problem via share sheet), **Privacy and Terms** (plain-language draft).
- Cards show "Launched 14 Oct" / "New · added 6 Oct"; sort chips Best match / Nearest / Newest.

### Found on the Pixel and fixed
1. Keyboard covered form fields on the setup page (list now shrinks with the keyboard).
2. Light theme: app pages kept dark text on a light background (pages now use the app theme).
3. Light theme: white status-bar icons were unreadable (now follow the theme).
4. "movies in nearby cinemas" read "Nearby Cinema" as a place (and would have geocoded it).
5. "nearest IT park" woke the on-device AI for nothing ("nearest" unknown).
6. Merchant event time showed 1:30 PM instead of 7 PM (time zone applied twice).
7. Picking a suggestion left the keyboard open; tapping outside a field didn't close it.
8. Widening a search replaced the query in the bar with just "within 6 km".

Also removed from the product UI: the engineering overlay ("AI: unparseable…"), and the camera button (visual search isn't built yet).

### Needs a server or your decision (not built)
- **Phone verification (OTP)**: needs an SMS provider (cost) and an auth service.
- **Merchant posts visible to everyone, merchant verification, moderation queue, reports**: needs merchant-service hosted (hosting decision still open).
- **Push notifications** for new launches: needs FCM/APNs + a server.
- **Movie / show listings with dates** ("latest movie releases"): needs a licensed data feed; today the app finds cinemas and hands off to BookMyShow / District.
- **Platform logos:** the booking page shows names with colour badges; real logos need the platforms' brand permission.
- **Languages** (Kannada / Hindi): Settings shows only what exists, so no language switch yet.
- Privacy Policy / Terms text is a plain-language **draft for legal review**.
- Untested: picking an actual photo (the picker opens and cancels fine; I didn't select your personal photos); iOS app (no Xcode).

| 4 | "Search by company name doesn't work (Microsoft, Wells Fargo…). Google finds them; users should search whatever they want." | Root causes: (a) the pack builder dropped **offices** (OSM has Microsoft ×7, Wells Fargo ×2, Accenture ×15, IBM ×17… as `office=company/it`); (b) names matched only exactly (no "wellsfargo", no typos); (c) the on-device model turned "microsoft" into cafés; (d) **the cache returned an arbitrary 5,000 rows per area**, so in dense Bengaluru places could silently go missing. Fixes: pack now includes all 3,110 named offices (31,310 places); `NameMatch` (spacing/case/punctuation-insensitive, per-word typo tolerance); name searches stay name searches (`NameIndex` in `FrameMerge`); **`search_text` column + migration 2** so searches filter by category/name before loading rows (no cap); **online name search via Photon** (OpenStreetMap-based, free) for names not on the phone; "The nearest, Wells Fargo, is 17.9 km away · Show results within 25 km". Verified on the Pixel: microsoft (Microsoft Lab 4.1 km, R&D 10.6 km, Microsoft India 13.8 km), wellsfargo, mirosoft. Note: Google shows more because Google Maps is a paid, proprietary database; OpenStreetMap coverage is what's mapped by volunteers. |
| 5 | "Search Around / Search Recently Launched text on the landing page is too big" | Door labels now use the 22 sp title style (were 34 sp display); tagline 28 sp. |

APK: `dist/pulse-0.4.1.apk` (versionCode 5). Tests at end of session 3: DSL 34, mobile core 90, Compose UI 17, all passing; iOS shared code compiles.

## Session 2, Monday 2026-10-12

| # | You asked | What happened |
|---|---|---|
| 14 | "continue" | Re-ran all suites (green). Resumed the Pixel AI test. |
| 15 | Pixel connected | Found and fixed: keyboard didn't close after search; system Back exited the app; the GPU path loads but fails on first generation ("Invalid command queue"), so the engine now smoke-tests and falls back to CPU. **Qwen ran end to end on the Pixel CPU: "refined in ~6 s".** Phone was on an AC charger, so stay-awake needed "any power source" (restored to off after). |
| 16 | Four QA/product issues in `prompts.md` (keyboard overlap, food-only/static results, specificity picker, role on first launch) | All four implemented and **verified on the Pixel 8a**. Details below. No UI Intent DSL schema change was needed. |

### The four issues

1. **Keyboard overlap (blocker).** Root cause: edge-to-edge mode ignores `adjustResize`, and the bar only padded for the nav bar. Fix: `Modifier.keyboardSafe()` (`WindowInsets.ime ∪ navigationBars`) on the bar, the diagnostic line and the list; shared `PulseTextField`. Works on iOS via Compose MP insets. Verified: the bar sits above Gboard while typing.
2. **Food-only and static.** Root causes: (a) hardcoded Koramangala location; (b) only ~22 sample listings, no fashion, none near you; (c) lexicon gaps; (d) results cached per query; and, found on the device, **(e) the 0.5B model parroted its example ("cafés in Koramangala") and overrode correct results.** Fixes: live location (Android `LocationManager`, iOS CoreLocation from Kotlin) with a 250 m movement gate, re-fetch on move or query change; all-category lexicon; +23 sample listings incl. north-west Bengaluru; **`FrameMerge`: the AI may fill gaps but never contradict the rules, and places must appear in what the user said**; the AI only wakes for words the rules can't explain. Verified: real GPS used; fashion returns fashion. Caveat: **no sample data exists near the phone's actual location (≈12.897, 77.529)**, so near-me lists are empty there until a real data source exists.
3. **Specificity picker.** `intent.category_picker` registered component; the composer adds it to any surface whose request names no category (also "Legacy"). Food/Fashion/Fitness/Events/Shopping repaint with that category and log a signal; "Other" opens the shared keyboard-safe field and goes through the AI pipeline. Verified on device.
4. **Role on first launch.** `UserRoleRepository` (SQLDelight; no expect/actual needed) + `onboarding.role_picker` component on a DSL surface + `RoleGate`/`RoleExperience` (merchant divergence = one line) + Settings (gear on landing). Verified: asked once, survives kill/restart, changeable in Settings.

Tests: DSL 34, mobile core 48, Compose UI 4, services 53, all passing. New APK: `dist/pulse-0.2.0.apk`.

| 17 | Search hotels/events/etc. anywhere (RR Nagar, Jayanagar…), not static, free and open source; "are we deviating?" | **No deviation**: it delivers the brief's "global-ready" and "what's around me" goals. Built on **OpenStreetMap (ODbL)**: 1,086-neighbourhood offline place pack (RR Nagar → Rajarajeshwari Nagar etc.), unknown areas geocoded via Nominatim, live Overpass client. Public Overpass servers proved unreliable (504s even for tiny queries), so I added an **offline Bengaluru region pack: 21,471 real places** extracted once from Geofabrik with `tools/osm-pack` (+1.5 MB APK). Live queries are only used outside covered regions. Also: exact-tag queries (regex queries timed out), gyms → all fitness, auto-widening radius with an honest note, hotels/pharmacies/hospitals/ATMs/fuel words, OSM attribution. Verified on the Pixel: hotels near RR Nagar (20), events in Jayanagar (Inox, galleries), gyms near me (19 within 5 km), pharmacies near me, What's New shows real recently-mapped places. APK: `dist/pulse-0.3.0.apk`. |
| 18 | "Do we need a pack for every city? Alternative?" | No. Bundling Bengaluru is a launch shortcut. Scalable path: catalog-service imports OSM for India/world weekly (no per-city work); the phone fetches small ~5 km tiles on demand into a capped LRU cache (e.g. 20–50 MB), with an optional "save city for offline". Cost: one server's hosting; data and software stay free and open source. |

| 19 | "Document what you changed so you know before resuming" | Wrote **`docs/CHANGES.md`**: the technical record (files, reasons, data flow, invariants, known issues, how to verify, device notes). Saved the neighbourhood-pack generator as `tools/osm-pack/build_place_pack.py` (verified it reproduces the pack exactly). |
| 20 | "On the location card only Go works; the other two aren't clickable" | Root cause: Save/Share went to `pulse://` links nobody handled. Built a **saved shelf** (bookmark toggles, "Saved to your shelf", listed in Settings, works offline) and **Share** (system share sheet with name, address, map link). **Testing on the Pixel also caught a launch crash when updating over the old version** (missing DB migration); fixed with a migration + test. Verified on the Pixel: Save, Share sheet (WhatsApp/Gmail…), shelf shows Lalitha Medicals. APK: `dist/pulse-0.3.1.apk`. |

| 21 | "yes please" (commit + push) | Committed and pushed as **`27a4319`** (91 files), co-authored by Ashok Kumar S and Claude. No secrets, APKs or models in git. |

Tests at end of session 2: DSL 34, mobile core 60, Compose UI 7, services 53, all passing.

### Still open after session 2
- **Places: solved for Bengaluru with OpenStreetMap.** Next: catalog-service OSM import + tile API so any city works without packs (needs a server to host it).
- **Events with dates** (tonight's shows): OSM has venues, not schedules. Needs merchant-posted events (Merchant role) or partner feeds.
- Header chip still shows the requested radius (≤ 2 km) when results widened to 5 km; the note below explains it.
- Qwen answer quality (copies examples; grounding now contains the damage; a small fine-tune or better few-shots would fix it).
- Voice test on the Pixel (needs you to speak).

---

## Session 1, Sunday 2026-10-11 Product owner: Sanjay Y M (@sanjayspear). Built with Claude Code.

### Where to resume (as of end of session 1)

1. **Finish the on-device AI test on the Pixel 8a.** Unlock the phone, open Pulse → What's New, type
   *"somewhere quiet to read with good coffee"*. Expect the bottom diagnostic to read
   `AI: refined in N ms` and the pill to say `on-device · on device llm`. The model is already
   side-loaded on the phone. The build on the phone has streaming + GPU, but the result is untested.
2. **Voice test.** Tap mic → "While using the app" → say "new cafes in Koramangala within 3 km".
3. **Commit + push the AI work.** It's on the Mac but **not in git yet** (see "Uncommitted work").
4. **Decide on more events** (Q4 below): real GPS location, a wider default radius, and a real data source.
5. Optional: Gemini Nano / Apple Foundation Models as extra accelerators; in-app download test on Wi-Fi.

## Asks and answers

| # | You asked | What happened |
|---|---|---|
| 1 | Read `prompt.md` | Summarized the Pulse brief: AI-first, intent-driven KMP classifieds app; UI generated from intent via a DSL; Spring Boot microservices; AI server. |
| 2 | "Start building" | Built the whole foundation. Decisions made with defaults (see `docs/01-decision-log.md`): the AI emits a compact intent frame and a deterministic composer paints the UI; rules first, LLM only when unsure; the model never invents content; cuboctahedron 3D buttons; MapLibre; REST BFF; Spring Boot 3.5.16, Kotlin 2.2.21, Compose MP 1.9.3. |
| 3 | "Attach screenshots once you are done" | Rendered 8 real screens headlessly into `docs/screenshots/` and published a private gallery: https://claude.ai/artifact/JF6QmvgEBe8zNG2gvbkzAu. The review caught and fixed a parser bug ("hmm" was treated as a search). |
| 4 | "Can you give me apk" | Explained the Android SDK was missing; offered to install it (license acceptance) or use CI. |
| 5 | "Option 1 … install all things required … build a good apk asap" | Installed the Android SDK, emulator and Pixel 7 image (licenses accepted on your behalf, as authorized). First Android compile needed one fix. APK went from 144 MB to **30 MB** (arm64 only + R8). Added app icon, dark launch theme, status-bar fixes. Signed with a local release key. Emulator smoke test passed. Output: `dist/pulse-0.1.0.apk`. |
| 6 | "How long … if I connect my Pixel … is the build ok" | Build OK. Explained what a real device adds (mic, real performance, Call/Go, your screen). |
| 7 | "Device is connected" | **Pixel 8a**, Tensor G3, 8 GB, Android 16. Cold start 336 ms; landing 0 janky frames; scroll 0.12% janky; response time 2 ms. Found and fixed: mic permission never requested, plus a speech error that could crash the app; content colliding with the status bar. The "Maps opened" scare was traced to someone touching the phone, not a bug. |
| 8 | "Does this build have an in-app AI model" | No. The rules engine only; the MediaPipe runtime was included but no model. Offered: Gemini Nano (Pixel/flagships, 0 MB), downloadable Gemma (550 MB), or bundled (too big). |
| 9 | "Make you a contributor in the repository" | Claude can't be a GitHub collaborator (no account). Credit is via `Co-authored-by:` trailers. Found your git identity unset (commits used `…@Sanjays-MacBook-Air.local`, which doesn't link to your profile). |
| 10 | "Make ashokslsk@gmail.com contributor with full access" | Set repo identity to `Sanjay Y M <sanjayspear@gmail.com>`. Added `CONTRIBUTORS.md`; commit `b672faf` co-authored by Ashok Kumar S and Claude; **pushed**. Collaborator access must be granted by you: GitHub → Settings → Collaborators → add **ashokslsk**. Personal repos have no "admin" role; true admin needs an organization. |
| 11 | "Push it to git" | Already pushed; `main` matched `origin/main`. |
| 12 | "Use what's universal for Android and iOS, not only Pixel" + "it should be open source" | Chose **Qwen2.5-0.5B-Instruct** (Apache-2.0, 521 MB, SHA-256 `e608953f…0452fd2`) via **MediaPipe LLM Inference on both platforms**. Gemma rejected (custom license, gated); 1.5B rejected (1.5 GB). Built the model catalog, resumable verified downloader (Android), Swift URLSession + CryptoKit downloader and MediaPipe bridge (iOS), hot-swap after download, "Add on-device AI · 521 MB" chip, and an AI diagnostic line. On the Pixel: model loads on the **GPU** (1272/1326 ops). First try timed out at the 2.5 s budget, so I switched to streaming with an early stop, GPU-first, and an 8 s budget (installed, untested: phone locked). |
| 13 | "I don't see a lot of events, why" | Only about 22 fictional sample listings around central Bengaluru; location is hardcoded to Koramangala (you're ~29 km away); "What's New" uses a 2 km radius; past events drop off. Fixes proposed below. |

## Current state

| Area | State |
|---|---|
| Repo | https://github.com/sanjayspear/Whatsanew (private), `main` at `b672faf` |
| Tests | DSL 31, mobile core 37, catalog-service 31 (+2 Docker ITs), other services 12 (+8 placeholders), AI server 6. All passing at last run. |
| Android | Release APK built and tested on emulator + Pixel 8a. Latest build (with AI) is at `apps/mobile/androidApp/build/outputs/apk/release/androidApp-release.apk`; `dist/pulse-0.1.0.apk` is the **older** pre-AI, pre-mic-fix build. |
| iOS | Shared Kotlin compiles for iOS; app not built (Xcode not installed). Uses XcodeGen + CocoaPods (`pod 'MediaPipeTasksGenAI'`). |
| On-device AI | Qwen2.5-0.5B on both platforms; Pixel test in progress. |
| Tooling installed on the Mac | Android SDK + emulator in `~/Library/Android/sdk`; AVD `pulse_test`; `apps/mobile/local.properties` points at the SDK. |

## Uncommitted work (on the Mac, not in git)

The Qwen on-device AI work: `OnDeviceModels.kt`, `AndroidModelManager.kt`, MediaPipe streaming backend,
`TieredAiEngine` refresh + diagnostics, AI chip in `DiscoverScreen`, iOS `MediaPipeBridge.swift` + `Podfile`.
Commit and push once the Pixel test passes.

## Keep safe (never in git)

- `apps/mobile/androidApp/pulse-release.jks` and `keystore.properties`: the Android signing key.
  **Every future update must be signed with it.** Back both up privately.

## Open decisions for you

1. Spring Boot 3.5 (support ended) vs moving to 4.x.
2. What "Legacy" should be (currently "browse every category").
3. User-posted listings (classifieds)? Adds moderation and payments.
4. **Event data source** for Bengaluru: partners, ticketing/venue APIs, or curated seed. Also approve real GPS location and a 5 km default radius.
5. Booking: deep-link to partners or in-app ticketing.
6. Cloud: AWS ap-south-1 (assumed) or GCP.
7. Fix the old commit `df9ec0e` author email (needs a force-push), or leave it.
8. Grant Ashok access on GitHub (you), and an organization if he needs admin.
