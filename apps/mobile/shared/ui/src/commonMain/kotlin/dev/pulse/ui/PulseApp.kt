package dev.pulse.ui

import androidx.compose.runtime.Composable
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
import dev.pulse.core.data.ResolvedItem
import dev.pulse.core.data.SqlProfileStore
import dev.pulse.core.data.SqlSignalLog
import dev.pulse.core.db.PulseDatabase
import dev.pulse.core.di.coreModule
import dev.pulse.core.pipeline.IntentPipeline
import dev.pulse.core.platform.PlatformContext
import dev.pulse.core.platform.createPlatformAiEngine
import dev.pulse.core.platform.createSpeechEngine
import dev.pulse.core.platform.createSqlDriver
import dev.pulse.core.prefs.ProfileStore
import dev.pulse.core.signals.Signal
import dev.pulse.core.signals.SignalLog
import dev.pulse.dsl.ActionKind
import dev.pulse.dsl.ActionSpec
import dev.pulse.dsl.Roles
import dev.pulse.ui.components.DefaultRegistry
import dev.pulse.ui.design.PulseTheme
import dev.pulse.ui.registry.ComponentRegistry
import dev.pulse.ui.registry.RenderScope
import dev.pulse.ui.screens.DiscoverScreen
import dev.pulse.ui.screens.LandingScreen
import kotlin.time.Clock
import kotlinx.coroutines.launch
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** The app's object graph, built once per process by each platform entry point. */
class PulseGraph(
    val pipeline: IntentPipeline,
    val resolver: ListingResolver,
    val registry: ComponentRegistry,
    val engine: AiEngine,
    val speech: SpeechEngine,
    val launcher: ActionLauncher,
    val languageTag: String,
    val nowMs: () -> Long,
) {
    fun renderScope(onIntent: (String) -> Unit) = RenderScope(
        registry = registry,
        resolve = resolver::resolve,
        dispatch = ::dispatch,
        signal = pipeline::record,
        submitIntent = onIntent,
        languageTag = languageTag,
        nowMs = nowMs,
        anchorOf = resolver::anchorOf,
    )

    fun dispatch(action: ActionSpec, item: ResolvedItem?) {
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
        fun create(
            context: PlatformContext,
            languageTag: String = "en-IN",
            launcher: ActionLauncher = platformActionLauncher(context),
            nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
        ): PulseGraph {
            val db = PulseDatabase(createSqlDriver(context))
            val koin = koinApplication {
                allowOverride(true)
                modules(
                    coreModule(engine = { gazetteer -> createPlatformAiEngine(context, gazetteer) }, catalog = DefaultRegistry, nowMs = nowMs),
                    module {
                        single<SignalLog> { SqlSignalLog(db) }
                        single<ProfileStore> { SqlProfileStore(db) }
                    },
                )
            }.koin
            return PulseGraph(
                pipeline = koin.get(), resolver = koin.get(), registry = DefaultRegistry, engine = koin.get(),
                speech = createSpeechEngine(context), launcher = launcher, languageTag = languageTag, nowMs = nowMs,
            )
        }
    }
}

enum class Screen { Landing, Discover }

/**
 * @param requestMicPermission platform hook (Android runtime permission / iOS prompt); returns
 *   whether the microphone may be used.
 */
@Composable
fun PulseApp(
    graph: PulseGraph,
    startScreen: Screen = Screen.Landing,
    requestMicPermission: suspend () -> Boolean = { true },
) {
    var screen by remember { mutableStateOf(startScreen) }
    var voiceDraft by remember { mutableStateOf<String?>(null) }
    val state by graph.pipeline.state.collectAsState()
    val scope = rememberCoroutineScope()
    val submit: (String) -> Unit = { text -> scope.launch { graph.pipeline.submit(IntentInput(text)) } }

    LaunchedEffect(Unit) { graph.engine.warmUp() }
    LaunchedEffect(Unit) {
        // ACT intents ("call the first one") resolve against the results currently on screen.
        graph.pipeline.actions.collect { action ->
            val results = graph.pipeline.state.value.surface?.root?.children?.firstOrNull { it.role == Roles.RESULTS }?.bind
            val index = action.args["target"]?.substringAfter('[')?.substringBefore(']')?.toIntOrNull() ?: 0
            val item = results?.let { graph.resolver.resolve(it).getOrNull(index) } ?: return@collect
            val template = results.template?.actions?.firstOrNull { it.kind == action.kind } ?: action
            graph.dispatch(template.copy(args = template.args.mapValues { (_, v) -> dev.pulse.ui.components.resolveArg(v, item) ?: v }), item)
        }
    }

    PulseTheme(state.theme) {
        when (screen) {
            Screen.Landing -> LandingScreen(
                onWhatsNew = { screen = Screen.Discover; submit("what's new near me") },
                onLegacy = { screen = Screen.Discover; submit("everything near me") },
            )
            Screen.Discover -> DiscoverScreen(
                state = state,
                scope = remember(graph) { graph.renderScope(submit) },
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
                listening = voiceDraft != null,
            )
        }
    }
}
