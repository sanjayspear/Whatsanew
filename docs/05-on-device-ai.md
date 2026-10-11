# On-device AI layer

## `AiEngine` (shared/core)

```kotlin
interface AiEngine {
    val status: StateFlow<EngineStatus>
    suspend fun warmUp()
    fun understand(input: IntentInput, context: IntentContext): Flow<FrameEvent> // Provisional, then optional Refined
    fun close()
}
expect fun createPlatformAiEngine(context: PlatformContext, gazetteer: Gazetteer): AiEngine
```

Implementations, all `TieredAiEngine` with different ladders:

| Platform | Ladder (first available wins) | Source |
|---|---|---|
| Android | Gemini Nano (AICore) → Gemma INT4 via MediaPipe LLM Inference → rules | `androidMain/.../Platform.android.kt`, `AndroidBackends.kt` |
| iOS | Apple Foundation Models (Swift bridge) → (P3) llama.cpp GGUF → rules | `iosMain/.../Platform.ios.kt`, `iosApp/Bridges/*.swift` |
| Desktop (dev) | rules | `desktopMain/.../Platform.desktop.kt` |

Guarantees, each covered by `AiLadderTest`:
- Confident rules skip the LLM.
- Garbage, a crash or a timeout in the LLM falls back to rules.
- Thermal throttling skips the LLM.
- Unavailable rungs are skipped.

## Model decision matrix

Figures are planning estimates for the perf floor (Snapdragon 7-series, 6 GB). P2's benchmark
harness replaces them with measured values before anything ships.

| Model | Format | Size on disk | Peak RAM | Frame latency (≈50 tok out) | Quality on golden set | Thermal | Verdict |
|---|---|---|---|---|---|---|---|
| Rules (lexicon + gazetteer) | data pack | ~150 KB / city | <5 MB | **<5 ms** | high on common phrasings, low on fuzzy | none | **Always on; first paint** |
| Gemini Nano (AICore) | system | 0 (OS-managed) | OS-managed | ~0.5-1.5 s | high | low (NPU) | **Use when present** (flagships only, not the SD7 floor) |
| **Gemma 3 1B IT, INT4, LoRA** | MediaPipe `.task` | ~0.55 GB | ~1.0-1.3 GB | ~1-2.5 s (GPU) | good after LoRA on frames | moderate | **Primary Android rung** |
| Gemma 2B INT4 | `.task` | ~1.3 GB | ~2 GB | ~2-4 s | better | high | Only ≥ 8 GB devices |
| Phi-3-mini 3.8B INT4 | GGUF | ~2.2 GB | ~3 GB | 4 s+ | best | high | **Rejected**: busts the 6 GB budget with the app |
| Apple Foundation Models (~3B) | system | 0 | OS-managed | ~0.5-1.5 s | high, plus guided generation | low | **Primary iOS rung** (iOS 26+, A17 Pro/M-series) |
| Gemma 3 1B GGUF Q4_K_M | llama.cpp | ~0.6 GB | ~1.1 GB | ~1-2 s (Metal) | good after LoRA | moderate | iOS fallback for older devices (P3) |

Why first paint still meets <300 ms: the LLM is **never on the first-paint path**. Rules paint
immediately and the LLM can only *refine*. Measured in the desktop harness, utterance → painted
surface takes under 1 ms for the pipeline itself (see screenshot latency pill), and the
`IntentPipelineTest` asserts < 300 ms.

## UI Intent DSL (`packages/ui-dsl`)

| Type | Purpose |
|---|---|
| `IntentFrame` | What the model emits: `task`, `query`, `theme`, `layout`, `action`, `extraOps`, `confidence`, `source` |
| `ListingQuery` | Locale-free content description: categories (taxonomy keys), text, `near` (here / place / point + radius), `time` (presets resolved in the venue's zone), freshness, price, openNow |
| `Surface` / `Node` | Tree of registry keys + component-owned `props` + optional `Binding` (query, rank, limit, item template) + `actions` |
| `UiPatch` / `UiOp` | `surface.replace`, `node.insert`, `node.remove`, `node.move`, `node.update`, `theme.update`; atomic; `SESSION` or `PROFILE` persistence |
| `ThemeSpec` / `ThemePatch` | mood, accent, radius, elevation, blur, type, motion, saturation (all clamped) |

### Five example payloads ([examples/](../packages/ui-dsl/examples))

1. *"Show me new cafes in Koramangala within 3 km"*: `DISCOVER`, `food.cafe`, place Koramangala, 3000 m, `NEW`
2. *"Any drama or theatre near Ravindra Kalakshetra this weekend?"*: `DISCOVER`, `arts.theatre`, place, `THIS_WEEKEND` (adds a trailers rail, booking, soonest-first)
3. *"Change my app theme to something warm and quieter."*: `CUSTOMIZE_THEME` `{mood: WARM, motionScale: 0.5, saturation: 0.75}`, persisted to profile
4. *"Move the map to the top of the screen."*: `CUSTOMIZE_LAYOUT [{move, role: map, position: 0}]`, which becomes a `node.move` op plus saved layout prefs
5. *Camera (dosa) + voice "Where can I get this near me, something cheap?"*: `DISCOVER`, `food.restaurant.south_indian`, text `dosa`, here 2 km, `priceLevelMax 1`

Composed output for #1 (abridged):

```json
{"ops":[{"type":"surface.replace","surface":{"id":"surface-1","root":{"id":"root","component":"layout.stack","role":"root","children":[
  {"id":"header","component":"hero.header","role":"header","props":{"query":{"categories":["food.cafe"],"near":{"anchor":{"type":"place","name":"Koramangala"},"radiusMeters":3000},"freshness":"NEW"}}},
  {"id":"map","component":"map.stack","role":"map","bind":{"query":{…},"rank":{"strategy":"DISTANCE"},"limit":50}},
  {"id":"results","component":"list.cards","role":"results","bind":{"query":{…},"limit":20,"template":{"id":"card","component":"glass.card",
     "actions":[{"kind":"NAVIGATE","args":{"lat":"$item.lat","lng":"$item.lng"}},{"kind":"CALL","args":{"phone":"$item.phone"}},{"kind":"SAVE","args":{"listingId":"$item.id"}},{"kind":"SHARE","args":{"listingId":"$item.id"}}]}}}
]}}}]}
```

## Renderer and registry

`SurfaceRenderer(surface, scope)` applies `PulseTheme(surface.theme)` and calls
`scope.RenderNode(root)`. `RenderNode` looks up `registry[node.component]`, and unknown keys render nothing.
Bound containers resolve their `Binding` through `ListingResolver` (on-device filter + `LocalRanker`)
and render the template once per item with `scope.withItem(item)`.

Shipped components: `layout.stack`, `hero.header`, `list.cards`, `list.videos`,
`intent.suggestions`, and the five reference components below.

| # | Component | Key | Status |
|---|---|---|---|
| 1 | GeometricRotatingButton | `geo.button` | Done: Canvas cuboctahedron, motion-token-aware |
| 2 | GlassCard | `glass.card` | Done: procedural art, facts, action sheet |
| 3 | MapStack | `map.stack` | Offline schematic layer done (ring, projection, clustering); MapLibre tiles underneath in P1 |
| 4 | VideoTile | `video.tile` | Poster/glass chrome done; inline player (focused tile only) in P1 |
| 5 | ActionSheet | `action.sheet` | Done: neumorphic, `$item` arg resolution |

To add a component: implement `PulseComponent { key; Render(node, scope, modifier) }` and
`DefaultRegistry + MyComponent`. The DSL and renderer don't change.
