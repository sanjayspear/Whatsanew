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
import dev.pulse.core.ai.SearchSuggestions
import dev.pulse.core.data.Gazetteer
import dev.pulse.core.data.ListingItem
import dev.pulse.core.data.ListingResolver
import dev.pulse.core.data.MerchantDraft
import dev.pulse.core.data.MerchantPosts
import dev.pulse.core.data.NameIndex
import dev.pulse.core.data.NameMatch
import dev.pulse.core.data.RegionCentre
import dev.pulse.core.data.SampleCatalog
import dev.pulse.core.data.SearchDefaults
import dev.pulse.core.data.booking.BookingDestinations
import dev.pulse.core.prefs.AppSettingsStore
import dev.pulse.core.prefs.ProfileRepository
import dev.pulse.core.prefs.SearchHistory
import dev.pulse.core.prefs.SqlKeyValueStore
import dev.pulse.core.prefs.UserProfile
import dev.pulse.dsl.ThemePatch
import dev.pulse.dsl.ThemeSpec
import dev.pulse.ui.screens.MenuDrawer
import kotlinx.coroutines.flow.MutableStateFlow
import dev.pulse.core.data.ListingSource
import dev.pulse.core.data.NearbySync
import dev.pulse.core.data.RegionPacks
import dev.pulse.ui.res.Res
import dev.pulse.core.data.SqlListingCache
import dev.pulse.core.data.osm.OverpassClient
import dev.pulse.core.data.osm.PhotonClient
import dev.pulse.core.data.osm.PlaceGeocoder
import dev.pulse.core.data.LocationProvider
import dev.pulse.core.data.LocationTracker
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

/** App identity shown in About and Settings. Keep in step with androidApp versionName. */
object AppInfo {
    const val NAME = "Search Around"
    const val VERSION = "0.4.1"
}

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
    val profiles: ProfileRepository,
    val history: SearchHistory,
    val settings: AppSettingsStore,
    val merchant: MerchantPosts,
    private val profileStore: ProfileStore,
    private val cache: SqlListingCache?,
) {
    /** Navigation requests from components ("Open Settings", Book Now). The host owns the back stack. */
    private val _navigation = MutableSharedFlow<Screen>(extraBufferCapacity = 4)
    val navigation: SharedFlow<Screen> = _navigation.asSharedFlow()

    private val _locationRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val locationRequests: SharedFlow<Unit> = _locationRequests.asSharedFlow()

    /** Null = not asked yet; set by the host after the permission prompt. */
    val locationGranted = MutableStateFlow<Boolean?>(null)

    /** The listing the Book Now page is for, and what the user had searched (kept for context). */
    val bookingTarget = MutableStateFlow<ResolvedItem?>(null)
    private var bookingQuery: String? = null

    /** Set by the platform entry point when it can show a photo picker. */
    var photoPicker: (suspend () -> ByteArray?)? = null

    /** Picker choices, role changes, pages: things surfaces can ask the app to do. */
    val session = object : SessionActions {
        override val role: UserRole? get() = roles.role.value
        override fun discover(query: ListingQuery, label: String) =
            pipeline.apply(IntentFrame(Task.DISCOVER, query = query), utterance = label, latencyMs = 0)
        override fun setRole(role: UserRole) = roles.setRole(role)
        override fun nearestBeyond(query: ListingQuery) = resolver.nearestBeyond(dev.pulse.dsl.Binding(query))
        override fun isSaved(listingId: String) = listingId in shelf.ids.value
        override fun saved() = shelf.all()

        override fun navigate(screen: Screen) { _navigation.tryEmit(screen) }
        override fun toast(message: String) { _messages.tryEmit(message) }
        override fun openUrl(url: String) { if (BookingDestinations.isSafeLink(url)) launcher.open(url) }
        override val appVersion: String get() = AppInfo.VERSION

        override val profile: UserProfile? get() = profiles.profile.value
        override fun saveProfile(profile: UserProfile) = profiles.save(profile)
        override fun profilePhoto() = profiles.photo()
        override fun setProfilePhoto(bytes: ByteArray?) = profiles.savePhoto(bytes)
        override suspend fun pickPhoto() = photoPicker?.invoke()
        override val canPickPhoto: Boolean get() = photoPicker != null
        override fun signOut() = this@PulseGraph.signOut()
        override fun deleteAllData() = this@PulseGraph.deleteAllData()

        override fun history() = history.entries.value
        override val historyEnabled: Boolean get() = history.enabled
        override fun setHistoryEnabled(enabled: Boolean) {
            history.enabled = enabled
            _messages.tryEmit(if (enabled) "Search history on" else "Search history off and cleared")
        }
        override fun removeHistory(text: String) = history.remove(text)
        override fun clearHistory() = history.clear()

        override fun settings() = settings.settings.value
        override fun setDefaultRadiusKm(km: Int) {
            settings.setDefaultRadiusKm(km)
            _messages.tryEmit("Searches now look $km km around you")
        }
        override fun setManualArea(name: String?) = settings.setManualArea(name)
        override val theme: ThemeSpec get() = pipeline.state.value.theme
        override fun setTheme(patch: ThemePatch) =
            pipeline.apply(IntentFrame(Task.CUSTOMIZE_THEME, theme = patch), pipeline.state.value.lastUtterance.orEmpty(), 0)
        override val locationStatus: String get() {
            settings.settings.value.manualArea?.let { return "Searching around ${it.name}, the area you chose." }
            return when (locationGranted.value) {
                true -> if (location.location.value != null) "Using your current location." else "Location allowed. Waiting for a fix…"
                false -> "Location is off, so searches start from central Bengaluru. Allow location or choose an area."
                null -> "We'll ask for location the first time you search."
            }
        }
        override fun requestLocation() { _locationRequests.tryEmit(Unit) }
        override val locationAllowed: Boolean get() = locationGranted.value == true

        override fun bookingTarget() = bookingTarget.value
        override val bookingContext: String? get() = bookingQuery
        override fun bookingOptions(item: ListingItem) = BookingDestinations.optionsFor(item)
        override fun isInstalled(androidPackage: String) = launcher.isInstalled(androidPackage)

        override fun merchantPosts() = merchant.posts.value
        override fun publish(draft: MerchantDraft, image: ByteArray?) = merchant.publish(draft, image)
        override fun removePost(id: String) = merchant.remove(id)
        override fun image(key: String): ByteArray? =
            if (key.startsWith("merchant.image.")) merchant.image(key.removePrefix("merchant.image.")) else null

        override fun reportProblem(kind: String, details: String) {
            val body = buildString {
                appendLine("Search Around problem report")
                appendLine("Type: $kind")
                if (details.isNotBlank()) appendLine("Details: ${details.trim()}")
                appendLine("Last search: ${pipeline.state.value.lastUtterance ?: "none"}")
                appendLine("App version: ${AppInfo.VERSION}")
            }
            launcher.share("Search Around: $kind", body)
        }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short confirmations ("Saved to your shelf") shown briefly at the bottom of the screen. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Suggestions while typing: history, categories and real place names nearby. */
    fun suggestions(typed: String): List<String> {
        val here = resolver.anchorOf(null)
        val names = cache?.nearMatching(here, 25_000, 40) { _, text -> NameMatch.score(text, typed) > 0 }
            ?.map { it.title(languageTag) }.orEmpty()
        return SearchSuggestions.complete(typed, history.entries.value.map { it.text }, names)
    }

    fun backfillSearchText() { cache?.backfillSearchText() }

    /** Forget who is signed in on this phone. Saved places stay (they're useful to the next profile too). */
    fun signOut() {
        profiles.clear()
        roles.clear()
        history.clear()
        pipeline.reset()
        _navigation.tryEmit(Screen.Landing)
    }

    /** Everything personal on this phone: profile, role, history, saved, posts, settings, look. */
    fun deleteAllData() {
        merchant.clearAll()
        shelf.clear()
        settings.reset()
        profileStore.saveTheme(ThemeSpec())
        profileStore.saveLayout(dev.pulse.dsl.LayoutPrefs())
        signOut()
        _messages.tryEmit("All your data on this phone was deleted")
    }

    fun renderScope(
        onIntent: (String) -> Unit,
        dataVersion: Any? = null,
        registry: ComponentRegistry = this.registry,
        dataStatus: DataStatus = DataStatus(),
    ) = RenderScope(
        registry = registry,
        resolve = { binding -> resolver.resolveWithin(binding) },
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
        // Save, Share and Book are app features, not OS links.
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
        if (action.kind == ActionKind.BOOK && item != null) {
            bookingTarget.value = item
            bookingQuery = pipeline.state.value.lastUtterance
            _navigation.tryEmit(Screen.Booking)
            return
        }
        item?.let {
            val kind = if (action.kind == ActionKind.SAVE) Signal.Kind.SAVE else Signal.Kind.TAP
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
            val kv = SqlKeyValueStore(db)
            val models = createModelManager(context)
            val location = createLocationTracker(context)
            val settings = AppSettingsStore(kv, Gazetteer.Bangalore)
            // "Near me" is the area the user chose in Settings, else the GPS fix, else central Bengaluru.
            val here = LocationProvider { settings.manualCoordinate() ?: location.location.value ?: RegionCentre }
            val cache = SqlListingCache(db).also { it.evictOlderThan(nowMs() - 7L * 24 * 60 * 60_000) }
            val http = if (useSampleData) null else createHttpClient()
            val geocoder = http?.let { PlaceGeocoder(it) }
            val packs = if (useSampleData) null else RegionPacks(cache, { code ->
                runCatching { Res.readBytes("files/regions/$code.json") }.getOrNull()
            }, nowMs, kv)
            val sync = http?.let {
                NearbySync(
                    OverpassClient(it), geocoder!!, { items -> cache.upsert(items, nowMs()) }, here, nowMs,
                    coveredOffline = { p -> packs?.covers(p) == true },
                    names = PhotonClient(it),
                )
            }
            NameIndex.knows = { text -> cache.searchTextsNear(here.lastKnown(), 25_000).any { NameMatch.score(it, text) > 0 } }
            val merchant = MerchantPosts(
                kv, Gazetteer.Bangalore, nowMs,
                upsert = { items -> cache.upsert(items, nowMs()) },
                delete = { id -> db.pulseQueries.deleteListing(id) },
                here = { here.lastKnown() },
            ).also { it.restore() }
            val koin = koinApplication {
                allowOverride(true)
                modules(
                    coreModule(engine = { gazetteer -> createPlatformAiEngine(context, gazetteer, models) }, catalog = DefaultRegistry, nowMs = nowMs),
                    module {
                        single<LocationProvider> { here }
                        if (!useSampleData) {
                            single<ListingSource> { cache }
                            single { ListingResolver(get(), get(), get(), get(), nowMs) { name -> geocoder?.known(name) } }
                        } else {
                            // Sample data plus anything merchants publish on this device.
                            single<ListingSource> {
                                val seed = SampleCatalog.listings(nowMs())
                                ListingSource { seed + merchant.posts.value.map { it.toListing() } }
                            }
                        }
                        single<SignalLog> { SqlSignalLog(db) }
                        single<ProfileStore> { SqlProfileStore(db) }
                    },
                )
            }.koin
            return PulseGraph(
                pipeline = koin.get(), resolver = koin.get(), registry = DefaultRegistry, engine = koin.get(), models = models,
                speech = createSpeechEngine(context), location = location, roles = SqlUserRoleRepository(db), sync = sync, packs = packs,
                shelf = SqlSavedShelf(db, nowMs), launcher = launcher, languageTag = languageTag, nowMs = nowMs,
                profiles = ProfileRepository(kv), history = SearchHistory(kv, nowMs), settings = settings, merchant = merchant,
                profileStore = koin.get(), cache = if (useSampleData) null else cache,
            )
        }
    }
}

/** Every place the app can show. Landing and Discover are screens; the rest are DSL page surfaces. */
enum class Screen { Landing, Discover, Profile, History, Saved, Settings, Help, About, Legal, Booking, Publish, MyPosts }

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
    /** Whether location is already allowed, checked without prompting. */
    locationPermitted: () -> Boolean = { false },
    /** Platform hook so status-bar icons stay readable (dark icons on the light theme). */
    onDarkThemeChanged: (Boolean) -> Unit = {},
) {
    var stack by remember { mutableStateOf(listOf(Screen.Landing).let { if (startScreen == Screen.Landing) it else it + startScreen }) }
    val screen = stack.last()
    fun go(target: Screen) {
        stack = when {
            target == Screen.Landing -> listOf(Screen.Landing)
            target in stack -> stack.subList(0, stack.indexOf(target) + 1)
            else -> stack + target
        }
    }
    fun back() {
        if (stack.size <= 1) return
        if (screen == Screen.Discover) graph.pipeline.reset()
        stack = stack.dropLast(1)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var voiceDraft by remember { mutableStateOf<String?>(null) }
    val state by graph.pipeline.state.collectAsState()
    val scope = rememberCoroutineScope()
    val submit: (String) -> Unit = { text ->
        graph.history.record(text)
        if (screen != Screen.Discover) go(Screen.Discover)
        scope.launch { graph.pipeline.submit(IntentInput(text)) }
    }
    // Door / menu entries: not the user's own words, so not recorded as history.
    val openSearchAround = { menuOpen = false; graph.pipeline.reset(); go(Screen.Discover) }
    val openRecentlyLaunched = {
        menuOpen = false
        go(Screen.Discover)
        scope.launch { graph.pipeline.submit(IntentInput("what's new near me")) }
        Unit
    }

    val modelState by graph.models.state.collectAsState()
    val engineStatus by graph.engine.status.collectAsState()
    val here by graph.location.location.collectAsState()
    val syncVersion by (graph.sync?.version ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    val syncing by (graph.sync?.syncing ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val syncNotice by (graph.sync?.lastError ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()
    val syncDetail by (graph.sync?.lastErrorDetail ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()

    val savedIds by graph.shelf.ids.collectAsState()
    val profile by graph.profiles.profile.collectAsState()
    val historyEntries by graph.history.entries.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val posts by graph.merchant.posts.collectAsState()
    val booking by graph.bookingTarget.collectAsState()
    val granted by graph.locationGranted.collectAsState()
    val photo = remember(profile) { graph.profiles.photo() }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        graph.messages.collect { message ->
            toast = message
            kotlinx.coroutines.delay(1_800)
            if (toast == message) toast = null
        }
    }
    LaunchedEffect(Unit) { graph.navigation.collect { menuOpen = false; go(it) } }

    val packsLoaded by (graph.packs?.loaded ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsState()
    val packsLoading by (graph.packs?.loading ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    // First launch: load the bundled Bengaluru pack (~28k real places) into the offline cache.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            graph.packs?.ensureLoaded()
            graph.backfillSearchText() // rows saved before the search_text column existed
        }
    }

    // Fetch real places for whatever is on screen, around wherever that is: new query, > 250 m move, or a new chosen area.
    LaunchedEffect(state.surface, here, settings.manualArea) { graph.sync?.syncFor(state.surface) }

    // Ask for location the first time a search opens (or when Settings asks), then follow the device.
    suspend fun askLocation() {
        val ok = requestLocationPermission()
        graph.locationGranted.value = ok
        if (ok) graph.location.start()
    }
    LaunchedEffect(Unit) {
        if (graph.locationGranted.value == null && locationPermitted()) {
            graph.locationGranted.value = true
            graph.location.start()
        }
    }
    LaunchedEffect(screen) { if (screen == Screen.Discover && graph.locationGranted.value != true) askLocation() }
    LaunchedEffect(Unit) { graph.locationRequests.collect { askLocation() } }
    LaunchedEffect(Unit) { graph.engine.warmUp() }
    // Hot-swap: the moment the model is verified, the engine adds the LLM rung (no restart).
    LaunchedEffect(modelState) { if (modelState == ModelState.Ready) graph.engine.refresh() }
    LaunchedEffect(Unit) {
        // ACT intents ("call the first one") resolve against the results currently on screen.
        graph.pipeline.actions.collect { action ->
            val results = graph.pipeline.state.value.surface?.root?.children?.firstOrNull { it.role == Roles.RESULTS }?.bind
            val index = action.args["target"]?.substringAfter('[')?.substringBefore(']')?.toIntOrNull() ?: 0
            val item = results?.let { graph.resolver.resolveWithin(it).items.getOrNull(index) } ?: return@collect
            val template = results.template?.actions?.firstOrNull { it.kind == action.kind } ?: action
            graph.dispatch(template.copy(args = template.args.mapValues { (_, v) -> dev.pulse.ui.components.resolveArg(v, item) ?: v }), item)
        }
    }

    // System Back closes the menu, then walks back through pages instead of exiting the app.
    BackHandler(enabled = menuOpen || stack.size > 1) { if (menuOpen) menuOpen = false else back() }

    val role by graph.roles.role.collectAsState()
    val darkTheme = remember(state.theme) { dev.pulse.ui.design.PulseTokens.from(state.theme).colors.isDark }
    LaunchedEffect(darkTheme) { onDarkThemeChanged(darkTheme) }

    PulseTheme(state.theme) {
        val chosen = role
        if (chosen == null || profile == null) {
            // First launch (or after sign-out): account setup, rendered from a DSL surface via the registry.
            SurfaceHost(AppSurfaces.onboarding().copy(theme = state.theme), remember(graph, role) { graph.renderScope(submit, dataVersion = role) }, toast = toast)
            return@PulseTheme
        }
        RoleGate(chosen) { experience ->
            // Pages re-render when anything they show changes.
            val pageVersion = listOf(savedIds, profile, historyEntries, settings, posts, booking, granted, here, state.theme, role)
            val pageScope = remember(graph, experience, pageVersion) { graph.renderScope(submit, dataVersion = pageVersion, registry = experience.registry) }
            when (screen) {
                Screen.Landing -> LandingScreen(
                    onSearchAround = openSearchAround,
                    onRecentlyLaunched = openRecentlyLaunched,
                    onMenu = { menuOpen = true },
                    onProfile = { go(Screen.Profile) },
                    avatarId = profile?.avatarId,
                    photo = photo,
                    name = profile?.name,
                )
                Screen.Discover -> DiscoverScreen(
                    state = state,
                    scope = remember(graph, here, syncVersion, syncing, syncNotice, packsLoaded, packsLoading, savedIds, experience, settings, posts) {
                        graph.renderScope(
                            submit,
                            dataVersion = listOf(here, syncVersion, packsLoaded, savedIds, settings, posts),
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
                                graph.history.record(final.text)
                                graph.pipeline.submit(IntentInput(final.text, setOf(Modality.VOICE)))
                            }
                        }
                    },
                    onCamera = null, // P3: camera sheet -> MediaPipe VisionAnalyzer -> IntentInput(visionTags)
                    onBack = { back() },
                    initialText = voiceDraft ?: state.lastUtterance.orEmpty(),
                    modelState = modelState,
                    llmDiagnostic = listOfNotNull(engineStatus.lastLlm?.let { "AI: $it" }, syncDetail?.let { "Data: $it" }).joinToString("\n").ifEmpty { null },
                    modelLabel = "${graph.models.model.displayName} · ${graph.models.model.sizeLabel}",
                    onDownloadModel = { scope.launch { graph.models.download() } },
                    listening = voiceDraft != null,
                    toast = toast,
                    onClear = { graph.pipeline.reset() },
                    suggest = { typed -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { graph.suggestions(typed) } },
                )
                else -> SurfaceHost(
                    when (screen) {
                        Screen.Profile -> AppSurfaces.profile()
                        Screen.History -> AppSurfaces.history()
                        Screen.Saved -> AppSurfaces.saved()
                        Screen.Settings -> AppSurfaces.settings()
                        Screen.Help -> AppSurfaces.help()
                        Screen.About -> AppSurfaces.about()
                        Screen.Legal -> AppSurfaces.legal()
                        Screen.Booking -> AppSurfaces.booking()
                        Screen.Publish -> AppSurfaces.publish()
                        else -> AppSurfaces.myPosts()
                    }.copy(theme = state.theme), // app pages follow the user's chosen look
                    pageScope,
                    onBack = { back() },
                    toast = toast,
                )
            }
            if (screen == Screen.Landing) {
                MenuDrawer(
                    open = menuOpen,
                    profile = profile,
                    photo = photo,
                    role = role,
                    onSelect = { menuOpen = false; go(it) },
                    onRecentlyLaunched = openRecentlyLaunched,
                    onSearchAround = openSearchAround,
                    onSignOut = { menuOpen = false; graph.signOut() },
                    onClose = { menuOpen = false },
                )
            }
        }
    }
}
