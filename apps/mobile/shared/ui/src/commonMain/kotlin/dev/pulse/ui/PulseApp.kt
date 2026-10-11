package dev.pulse.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.pulse.core.ai.AiEngine
import dev.pulse.core.ai.IntentInput
import dev.pulse.core.ai.Modality
import dev.pulse.core.ai.SpeechEngine
import dev.pulse.core.ai.Transcript
import dev.pulse.core.data.ListingResolver
import dev.pulse.core.data.ListingSource
import dev.pulse.core.data.NearbySync
import dev.pulse.core.data.RegionPacks
import dev.pulse.ui.res.Res
import dev.pulse.core.data.SqlListingCache
import dev.pulse.core.data.osm.OverpassClient
import dev.pulse.core.data.osm.PlaceGeocoder
import dev.pulse.core.data.LocationProvider
import dev.pulse.core.data.LocationTracker
import dev.pulse.core.data.asProvider
import dev.pulse.core.data.ResolvedItem
import dev.pulse.core.data.SqlProfileStore
import dev.pulse.core.data.SqlSignalLog
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.di.coreModule
import dev.pulse.core.pipeline.IntentPipeline
import dev.pulse.core.platform.PlatformContext
import dev.pulse.core.platform.createHttpClient
import dev.pulse.core.platform.createLocationTracker
import dev.pulse.core.platform.createModelManager
import dev.pulse.core.platform.createPlatformAiEngine
import dev.pulse.core.platform.createSpeechEngine
import dev.pulse.core.platform.createSqlDriver
import dev.pulse.core.model.ModelManager
import dev.pulse.core.model.ModelState
import dev.pulse.core.prefs.ProfileStore
import dev.pulse.core.prefs.SavedShelf
import dev.pulse.core.prefs.SqlSavedShelf
import dev.pulse.core.prefs.SqlUserRoleRepository
import dev.pulse.core.prefs.UserRole
import dev.pulse.core.prefs.UserRoleRepository
import dev.pulse.core.signals.Signal
import dev.pulse.core.signals.SignalLog
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.ActionSpec
import dev.pulse.dsl.IntentFrame
import dev.pulse.dsl.ListingQuery
import dev.pulse.dsl.Roles
import dev.pulse.dsl.Task
import dev.pulse.ui.components.DefaultRegistry
import dev.pulse.ui.design.PulseTheme
import dev.pulse.ui.registry.ComponentRegistry
import dev.pulse.ui.registry.DataStatus
import dev.pulse.ui.registry.RenderScope
import dev.pulse.ui.registry.RoleGate
import dev.pulse.ui.registry.SessionActions
import dev.pulse.ui.screens.DiscoverScreen
import dev.pulse.ui.screens.AppSurfaces
import dev.pulse.ui.screens.LandingScreen
import dev.pulse.ui.screens.SurfaceHost
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** The app's object graph, built once per process by each platform entry point. */
class PulseGraph(
    val pipeline: IntentPipeline,
    val resolver: ListingResolver,
    val registry: ComponentRegistry,
    val engine: AiEngine,
    val models: ModelManager,
    val speech: SpeechEngine,
    val location: LocationTracker,
    val roles: UserRoleRepository,
    val sync: NearbySync?,
    val packs: RegionPacks?,
    val shelf: SavedShelf,
    val launcher: ActionLauncher,
    val languageTag: String,
    val nowMs: () -> Long,
) {
    /** Picker choices, role changes: things surfaces can ask the app to do. */
    val session = object : SessionActions {
        override val role: UserRole? get() = roles.role.value
        override fun discover(query: ListingQuery, label: String) =
            pipeline.apply(IntentFrame(Task.DISCOVER, query = query), utterance = label, latencyMs = 0)
        override fun setRole(role: UserRole) = roles.setRole(role)
        override fun isSaved(listingId: String) = listingId in shelf.ids.value
        override fun saved() = shelf.all()
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short confirmations ("Saved to your shelf") shown briefly at the bottom of the screen. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun renderScope(
        onIntent: (String) -> Unit,
        dataVersion: Any? = null,
        registry: ComponentRegistry = this.registry,
        dataStatus: DataStatus = DataStatus(),
    ) = RenderScope(
        registry = registry,
        resolve = { binding -> resolver.resolveWidening(binding) },
        dispatch = ::dispatch,
        signal = pipeline::record,
        submitIntent = onIntent,
        languageTag = languageTag,
        nowMs = nowMs,
        anchorOf = resolver::anchorOf,
        dataVersion = dataVersion,
        session = session,
        dataStatus = dataStatus,
    )

    fun dispatch(action: ActionSpec, item: ResolvedItem?) {
        // Save and Share are app features, not OS links.
        if (action.kind == ActionKind.SAVE && item != null) {
            val nowSaved = shelf.toggle(item.item)
            if (nowSaved) pipeline.record(Signal(Signal.Kind.SAVE, item.item.category, item.item.id, nowMs()))
            _messages.tryEmit(if (nowSaved) "Saved to your shelf" else "Removed from your shelf")
            return
        }
        if (action.kind == ActionKind.SHARE && item != null) {
            val (subject, text) = shareText(item)
            launcher.share(subject, text)
            pipeline.record(Signal(Signal.Kind.TAP, item.item.category, item.item.id, nowMs()))
            return
        }
        item?.let {
            val kind = when (action.kind) {
                ActionKind.SAVE -> Signal.Kind.SAVE
                ActionKind.BOOK -> Signal.Kind.ATTEND
                else -> Signal.Kind.TAP
            }
            pipeline.record(Signal(kind, it.item.category, it.item.id, nowMs()))
        }
        ActionUris.of(action, item)?.let(launcher::open)
    }

    companion object {
        /**
         * @param useSampleData fictional seed listings instead of live OpenStreetMap data. Used by the
         *   desktop screenshot harness and tests so they stay deterministic and offline.
         */
        fun create(
            context: PlatformContext,
            languageTag: String = "en-IN",
            launcher: ActionLauncher = platformActionLauncher(context),
            nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
            useSampleData: Boolean = false,
        ): PulseGraph {
            val db = PulseDatabase(createSqlDriver(context))
            val models = createModelManager(context)
            val location = createLocationTracker(context)
            val cache = SqlListingCache(db).also { it.evictOlderThan(nowMs() - 7L * 24 * 60 * 60_000) }
            val http = if (useSampleData) null else createHttpClient()
            val geocoder = http?.let { PlaceGeocoder(it) }
            val packs = if (useSampleData) null else RegionPacks(cache, { code ->
                runCatching { Res.readBytes("files/regions/$code.json") }.getOrNull()
            }, nowMs)
            val sync = http?.let {
                NearbySync(
                    OverpassClient(it), geocoder!!, { items -> cache.upsert(items, nowMs()) }, location.asProvider(), nowMs,
                    coveredOffline = { p -> packs?.covers(p) == true },
                )
            }
            val koin = koinApplication {
                allowOverride(true)
                modules(
                    coreModule(engine = { gazetteer -> createPlatformAiEngine(context, gazetteer, models) }, catalog = DefaultRegistry, nowMs = nowMs),
                    module {
                        single<LocationProvider> { location.asProvider() }
                        if (!useSampleData) {
                            single<ListingSource> { cache }
                            single { ListingResolver(get(), get(), get(), get(), nowMs) { name -> geocoder?.known(name) } }
                        }
                        single<SignalLog> { SqlSignalLog(db) }
                        single<ProfileStore> { SqlProfileStore(db) }
                    },
                )
            }.koin
            return PulseGraph(
                pipeline = koin.get(), resolver = koin.get(), registry = DefaultRegistry, engine = koin.get(), models = models,
                speech = createSpeechEngine(context), location = location, roles = SqlUserRoleRepository(db), sync = sync, packs = packs, shelf = SqlSavedShelf(db, nowMs), launcher = launcher, languageTag = languageTag, nowMs = nowMs,
            )
        }
    }
}

enum class Screen { Landing, Discover, Settings }

/**
 * @param requestMicPermission platform hook (Android runtime permission / iOS prompt); returns
 *   whether the microphone may be used.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PulseApp(
    graph: PulseGraph,
    startScreen: Screen = Screen.Landing,
    requestMicPermission: suspend () -> Boolean = { true },
    requestLocationPermission: suspend () -> Boolean = { true },
) {
    var screen by remember { mutableStateOf(startScreen) }
    var voiceDraft by remember { mutableStateOf<String?>(null) }
    val state by graph.pipeline.state.collectAsState()
    val scope = rememberCoroutineScope()
    val submit: (String) -> Unit = { text -> scope.launch { graph.pipeline.submit(IntentInput(text)) } }

    val modelState by graph.models.state.collectAsState()
    val engineStatus by graph.engine.status.collectAsState()
    val here by graph.location.location.collectAsState()
    val syncVersion by (graph.sync?.version ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    val syncing by (graph.sync?.syncing ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val syncNotice by (graph.sync?.lastError ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()
    val syncDetail by (graph.sync?.lastErrorDetail ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()

    val savedIds by graph.shelf.ids.collectAsState()
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        graph.messages.collect { message ->
            toast = message
            kotlinx.coroutines.delay(1_800)
            if (toast == message) toast = null
        }
    }

    val packsLoaded by (graph.packs?.loaded ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsState()
    val packsLoading by (graph.packs?.loading ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    // First launch: load the bundled Bengaluru pack (~21k real places) into the offline cache.
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { graph.packs?.ensureLoaded() } }

    // Fetch real places for whatever is on screen, around wherever that is: new query or > 250 m move.
    LaunchedEffect(state.surface, here) { graph.sync?.syncFor(state.surface) }

    // Ask for location the first time a generated surface opens, then follow the device.
    LaunchedEffect(screen) {
        if (screen == Screen.Discover && requestLocationPermission()) graph.location.start()
    }
    LaunchedEffect(Unit) { graph.engine.warmUp() }
    // Hot-swap: the moment the model is verified, the engine adds the LLM rung (no restart).
    LaunchedEffect(modelState) { if (modelState == ModelState.Ready) graph.engine.refresh() }
    LaunchedEffect(Unit) {
        // ACT intents ("call the first one") resolve against the results currently on screen.
        graph.pipeline.actions.collect { action ->
            val results = graph.pipeline.state.value.surface?.root?.children?.firstOrNull { it.role == Roles.RESULTS }?.bind
            val index = action.args["target"]?.substringAfter('[')?.substringBefore(']')?.toIntOrNull() ?: 0
            val item = results?.let { graph.resolver.resolveWidening(it).items.getOrNull(index) } ?: return@collect
            val template = results.template?.actions?.firstOrNull { it.kind == action.kind } ?: action
            graph.dispatch(template.copy(args = template.args.mapValues { (_, v) -> dev.pulse.ui.components.resolveArg(v, item) ?: v }), item)
        }
    }

    // System Back on a generated surface returns to the landing doors instead of exiting the app.
    BackHandler(enabled = screen != Screen.Landing) {
        if (screen == Screen.Discover) graph.pipeline.reset()
        screen = Screen.Landing
    }

    val role by graph.roles.role.collectAsState()

    PulseTheme(state.theme) {
        val chosen = role
        if (chosen == null) {
            // First launch only: the role question, rendered from a DSL surface via the registry.
            SurfaceHost(AppSurfaces.onboarding(), remember(graph) { graph.renderScope(submit) })
            return@PulseTheme
        }
        RoleGate(chosen) { experience ->
        when (screen) {
            Screen.Landing -> LandingScreen(
                onWhatsNew = { screen = Screen.Discover; submit("what's new near me") },
                onLegacy = { screen = Screen.Discover; submit("everything near me") },
                onSettings = { screen = Screen.Settings },
            )
            Screen.Settings -> SurfaceHost(
                AppSurfaces.settings(),
                remember(graph, experience, savedIds) { graph.renderScope(submit, dataVersion = savedIds, registry = experience.registry) },
                onBack = { screen = Screen.Landing },
                toast = toast,
            )
            Screen.Discover -> DiscoverScreen(
                state = state,
                scope = remember(graph, here, syncVersion, syncing, syncNotice, packsLoaded, packsLoading, savedIds, experience) {
                    graph.renderScope(
                        submit,
                        dataVersion = listOf(here, syncVersion, packsLoaded, savedIds),
                        registry = experience.registry,
                        dataStatus = DataStatus(loading = syncing || packsLoading, notice = syncNotice),
                    )
                },
                onSubmit = submit,
                onMic = {
                    scope.launch {
                        if (!graph.speech.isAvailable || !requestMicPermission()) return@launch
                        voiceDraft = ""
                        // Speech failures (no match, busy recognizer, revoked permission) must never crash the app.
                        val final = runCatching {
                            var last: Transcript? = null
                            graph.speech.listen(graph.languageTag).collect { t ->
                                last = t
                                voiceDraft = t.text
                            }
                            last as? Transcript.Final
                        }.getOrNull()
                        voiceDraft = null
                        if (final != null && final.text.isNotBlank()) {
                            graph.pipeline.submit(IntentInput(final.text, setOf(Modality.VOICE)))
                        }
                    }
                },
                onCamera = { /* P3: camera sheet -> MediaPipe VisionAnalyzer -> IntentInput(visionTags) */ },
                onBack = { screen = Screen.Landing; graph.pipeline.reset() },
                initialText = voiceDraft ?: state.lastUtterance.orEmpty(),
                modelState = modelState,
                llmDiagnostic = listOfNotNull(engineStatus.lastLlm?.let { "AI: $it" }, syncDetail?.let { "Data: $it" }).joinToString("\n").ifEmpty { null },
                modelLabel = "${graph.models.model.displayName} · ${graph.models.model.sizeLabel}",
                onDownloadModel = { scope.launch { graph.models.download() } },
                listening = voiceDraft != null,
                toast = toast,
            )
        }
        }
    }
}
