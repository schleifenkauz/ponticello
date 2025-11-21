package ponticello.model.project

import kotlinx.serialization.serializer
import ponticello.model.GlobalSettings
import ponticello.model.PlaybackSettings
import ponticello.model.ServerOptions
import ponticello.model.code.GlobalPatternRegistry
import ponticello.model.code.OSCHookRegistry
import ponticello.model.code.ScriptRegistry
import ponticello.model.flow.AudioFlows
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.live.LauncherGrid
import ponticello.model.live.LiveObjectRegistry
import ponticello.model.obj.ContextualObject
import ponticello.model.player.ClockRegistry
import ponticello.model.player.MeterRegistry
import ponticello.model.record.LiveBufferRegistry
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.Score
import ponticello.model.server.BufferRegistry
import ponticello.model.server.BusRegistry
import ponticello.ui.launcher.PonticelloLauncher

val UI_STATE = Component<UIState>("ui-state", "UI State", UIState::default)

val CLOCKS = Component<ClockRegistry>("clocks", "Clocks", ClockRegistry::createDefault)

val METERS = Component<MeterRegistry>("meters", "Meters", MeterRegistry::createDefault)

val BUSSES = Component<BusRegistry>(
    "busses", "Busses", BusRegistry::createDefault,
    ObjectListSerializer(serializer(), ::BusRegistry)
)
val BUFFERS = Component<BufferRegistry>("buffers", "Buffers", BufferRegistry::createDefault)
val PATTERNS = Component(
    "patterns", "Patterns", GlobalPatternRegistry::createDefault,
    MultiFileComponentSerializer(
        ::GlobalPatternRegistry,
        listSerializer = GlobalPatternRegistry.Serializer,
        extension = "pattern.json"
    )
)
val INSTRUMENTS = Component(
    "instruments", "Instruments", InstrumentRegistry::createDefault,
    MultiFileComponentSerializer(::InstrumentRegistry, extension = "instr.json")
)
val FLOWS = Component(
    "flows", "Flows", AudioFlows::createDefault, AudioFlowsSerializer
).onSave { flows -> flows.writeVSTPluginStates() }

val SERVER_OPTIONS = Component<ServerOptions>("server_options", "Server Options", ServerOptions::default)

val OBJECTS = Component(
    "objects", "Score Objects", ScoreObjectRegistry::createDefault,
    MultiFileComponentSerializer(::ScoreObjectRegistry, extension = "obj.json")
)

val LIVE_OBJECTS = Component(
    "live_objects", "Live Objects", LiveObjectRegistry::createDefault,
    MultiFileComponentSerializer(
        ::LiveObjectRegistry,
        listSerializer = LiveObjectRegistry.Serializer,
        extension = "live.json"
    )
)

val LIVE_BUFFERS = Component(
    "live_buffers", "Live Buffers", LiveBufferRegistry::createDefault,
    SingleFileComponentSerializer(LiveBufferRegistry.serializer())
)

val SCRIPTS = Component(
    "scripts", "Scripts", ScriptRegistry::createDefault,
    MultiFileComponentSerializer(::ScriptRegistry, extension = "script.json")
)

val SCORE = Component<Score>("score", "Score", ::Score)

val LAUNCHER_GRID = Component<LauncherGrid>("launcher_grid", "Launcher Grid", { LauncherGrid.createNByN(4) })

val OSC_HOOKS = Component(
    "osc_hooks", "OSC Hooks", OSCHookRegistry::createDefault,
    MultiFileComponentSerializer(::OSCHookRegistry, extension = "osc.json")
)

val PLAYBACK_SETTINGS = Component<PlaybackSettings>("playback_settings", "Playback Settings", {
    PlaybackSettings.createDefault(
        PonticelloLauncher.rootContext[GlobalSettings]
    )
})

val allComponents = listOf<Component<out ContextualObject>>(
    METERS, CLOCKS,
    BUSSES, BUFFERS,
    PATTERNS, INSTRUMENTS,
    UI_STATE, FLOWS, SERVER_OPTIONS, PLAYBACK_SETTINGS,
    OBJECTS, LIVE_OBJECTS, LIVE_BUFFERS, SCORE, SCRIPTS, LAUNCHER_GRID, OSC_HOOKS
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