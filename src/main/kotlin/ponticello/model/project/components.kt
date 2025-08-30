package ponticello.model.project

import kotlinx.serialization.serializer
import ponticello.model.GlobalSettings
import ponticello.model.PlaybackSettings
import ponticello.model.ServerOptions
import ponticello.model.flow.AudioFlows
import ponticello.model.live.LauncherGrid
import ponticello.model.live.LiveObjectRegistry
import ponticello.model.obj.ContextualObject
import ponticello.model.obj.ScriptRegistry
import ponticello.model.registry.*
import ponticello.model.score.Score
import ponticello.ui.launcher.PonticelloLauncher

val UI_STATE = Component<UIState>("ui-state", UIState::default)

val CLOCKS = Component<ClockRegistry>("clocks", ClockRegistry::createDefault)

val METERS = Component<MeterRegistry>("meters", MeterRegistry::createDefault)

val BUSSES = Component<BusRegistry>(
    "busses", BusRegistry::createDefault,
    ObjectListSerializer(serializer(), ::BusRegistry)
)
val BUFFERS = Component<BufferRegistry>("buffers", BufferRegistry::createDefault)
val PATTERNS = Component(
    "patterns", GlobalPatternRegistry::createDefault,
    MultiFileComponentSerializer(::GlobalPatternRegistry, listSerializer = GlobalPatternRegistry.Serializer)
)
val INSTRUMENTS = Component(
    "instruments", InstrumentRegistry::createDefault,
    MultiFileComponentSerializer(::InstrumentRegistry)
)
val FLOWS = Component(
    "flows", AudioFlows::createDefault, AudioFlowsSerializer
).onSave { flows -> flows.writeVSTPluginStates() }

val SERVER_OPTIONS = Component<ServerOptions>("server_options", ServerOptions::default)
val OBJECTS = Component(
    "objects", ScoreObjectRegistry::createDefault,
    MultiFileComponentSerializer(::ScoreObjectRegistry)
)

val LIVE_TASKS = Component(
    "live_tasks", LiveObjectRegistry::createDefault,
    MultiFileComponentSerializer(::LiveObjectRegistry, listSerializer = LiveObjectRegistry.Serializer)
)

val SCRIPTS = Component(
    "scripts", ScriptRegistry::createDefault,
    MultiFileComponentSerializer(::ScriptRegistry)
)

val SCORE = Component<Score>("score", ::Score)

val LAUNCHER_GRID = Component<LauncherGrid>("launcher_grid", { LauncherGrid.createNByN(4) })

val PLAYBACK_SETTINGS = Component<PlaybackSettings>("playback_settings", {
    PlaybackSettings.createDefault(
        PonticelloLauncher.rootContext[GlobalSettings]
    )
})

val allComponents = listOf<Component<out ContextualObject>>(
    METERS, CLOCKS,
    BUSSES, BUFFERS,
    PATTERNS, INSTRUMENTS,
    UI_STATE, FLOWS, SERVER_OPTIONS, PLAYBACK_SETTINGS,
    OBJECTS, LIVE_TASKS, SCORE, SCRIPTS, LAUNCHER_GRID
)

inline operator fun <reified T : ContextualObject> PonticelloProject.get(component: Component<out T>) =
    components[component] as T

val PonticelloProject.mainScore get() = get(SCORE)
val PonticelloProject.busses get() = get(BUSSES)
val PonticelloProject.buffers get() = get(BUFFERS)
val PonticelloProject.patterns get() = get(PATTERNS)
val PonticelloProject.instruments get() = get(INSTRUMENTS)
val PonticelloProject.objects get() = get(OBJECTS)
val PonticelloProject.flows get() = get(FLOWS)
val PonticelloProject.uiState get() = get(UI_STATE)
val PonticelloProject.scripts get() = get(SCRIPTS)