package ponticello.model.project

import kotlinx.serialization.serializer
import ponticello.model.ScriptObject
import ponticello.model.ServerOptions
import ponticello.model.flow.AudioFlows
import ponticello.model.live.LauncherGrid
import ponticello.model.live.LiveTaskRegistry
import ponticello.model.obj.ContextualObject
import ponticello.model.registry.*
import ponticello.model.score.Score

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
val SYNTH_DEFS = Component(
    "instruments", SynthDefRegistry::createDefault,
    MultiFileComponentSerializer(::SynthDefRegistry)
)
val PROCESS_DEFS = Component(
    "processDefs", ProcessDefRegistry::createDefault,
    MultiFileComponentSerializer(::ProcessDefRegistry)
)
val FLOWS = Component(
    "flows", AudioFlows::createDefault,
    MultiFileComponentSerializer(::AudioFlows)
).onSave { flows -> flows.writeVSTPluginStates() }

val SERVER_OPTIONS = Component<ServerOptions>("server_options", ServerOptions::default)
val OBJECTS = Component(
    "objects", ScoreObjectRegistry::createDefault,
    MultiFileComponentSerializer(::ScoreObjectRegistry)
)

val LIVE_TASKS = Component(
    "live_tasks", LiveTaskRegistry::createDefault,
    MultiFileComponentSerializer(::LiveTaskRegistry, listSerializer = LiveTaskRegistry.Serializer)
)

val SCORE = Component<Score>("score", ::Score)

val LAUNCHER_GRID = Component<LauncherGrid>("launcher_grid", { LauncherGrid.createNByN(4) })

val allComponents = listOf<Component<out ContextualObject>>(
    METERS, CLOCKS,
    BUSSES, BUFFERS,
    PATTERNS, SYNTH_DEFS, PROCESS_DEFS,
    UI_STATE, FLOWS, SERVER_OPTIONS,
    OBJECTS, LIVE_TASKS, SCORE, LAUNCHER_GRID
) + ScriptObject.Type.entries.map { type -> type.component }

inline operator fun <reified T : ContextualObject> PonticelloProject.get(component: Component<out T>) =
    components[component] as T

val PonticelloProject.mainScore get() = get(SCORE)
val PonticelloProject.busses get() = get(BUSSES)
val PonticelloProject.buffers get() = get(BUFFERS)
val PonticelloProject.patterns get() = get(PATTERNS)
val PonticelloProject.instruments get() = get(SYNTH_DEFS)
val PonticelloProject.objects get() = get(OBJECTS)
val PonticelloProject.flows get() = get(FLOWS)
val PonticelloProject.settings get() = get(UI_STATE)