package ponticello.model.project

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import ponticello.model.ScriptObject
import ponticello.model.ServerOptions
import ponticello.model.flow.AudioFlows
import ponticello.model.live.LauncherGrid
import ponticello.model.live.LiveTaskRegistry
import ponticello.model.obj.ContextualObject
import ponticello.model.registry.*
import ponticello.model.score.Score

data class Component<T>(val name: String, val serializer: KSerializer<T>, val default: () -> T)

inline fun <reified T> component(
    name: String,
    noinline default: () -> T,
    serializer: KSerializer<T> = serializer<T>(),
) = Component(name, serializer, default)

val UI_STATE = component<UIState>("ui-state", UIState::default)

val CLOCKS = component<ClockRegistry>("clocks", ClockRegistry::createDefault)

val METERS = component<MeterRegistry>("meters", MeterRegistry::createDefault)

val BUSSES = component<BusRegistry>(
    "busses", BusRegistry::createDefault,
    ObjectListSerializer(serializer(), ::BusRegistry)
)
val BUFFERS = component<BufferRegistry>("buffers", BufferRegistry::createDefault)
val PATTERNS = component<GlobalPatternRegistry>("patterns", GlobalPatternRegistry::createDefault)
val SYNTH_DEFS = component<SynthDefRegistry>(
    "instruments", SynthDefRegistry::createDefault,
    ObjectListSerializer(serializer(), ::SynthDefRegistry)
)
val PROCESS_DEFS = component<ProcessDefRegistry>(
    "processDefs", ProcessDefRegistry::createDefault,
    ObjectListSerializer(serializer(), ::ProcessDefRegistry)
)
val FLOWS = component<AudioFlows>(
    "flows", AudioFlows::createDefault,
    ObjectListSerializer(serializer(), ::AudioFlows)
)

val SERVER_OPTIONS = component<ServerOptions>("server_options", ServerOptions::default)
val OBJECTS = component<ScoreObjectRegistry>(
    "objects", ScoreObjectRegistry::createDefault,
    ObjectListSerializer(serializer(), ::ScoreObjectRegistry)
)
val LIVE_TASKS = component<LiveTaskRegistry>("live_tasks", LiveTaskRegistry::createDefault)

val SCORE = component<Score>("score", ::Score)

val LAUNCHER_GRID = component<LauncherGrid>("launcher_grid", { LauncherGrid.createNByN(4) })

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