package xenakis.model.project

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import xenakis.model.ScriptObject
import xenakis.model.ServerOptions
import xenakis.model.flow.AudioFlows
import xenakis.model.obj.ContextualObject
import xenakis.model.registry.*
import xenakis.model.score.Score

data class Component<T>(val name: String, val serializer: KSerializer<T>, val default: () -> T)

inline fun <reified T> component(
    name: String,
    noinline default: () -> T,
    serializer: KSerializer<T> = serializer<T>(),
) = Component(name, serializer, default)

val UI_STATE = component<UIState>("ui-state", UIState::default)
val GROUPS = component<GroupRegistry>(
    "groups", GroupRegistry::createDefault,
    NamedObjectListSerializer(serializer(), ::GroupRegistry)
)
val BUSSES = component<BusRegistry>(
    "busses", BusRegistry::createDefault,
    NamedObjectListSerializer(serializer(), ::BusRegistry)
)
val BUFFERS = component<BufferRegistry>("buffers", BufferRegistry::createDefault)
val PATTERNS = component<GlobalPatternRegistry>("patterns", GlobalPatternRegistry::createDefault)
val SYNTH_DEFS = component<SynthDefRegistry>(
    "instruments", SynthDefRegistry::createDefault,
    NamedObjectListSerializer(serializer(), ::SynthDefRegistry)
)
val PROCESS_DEFS = component<ProcessDefRegistry>(
    "processDefs", ProcessDefRegistry::createDefault,
    NamedObjectListSerializer(serializer(), ::ProcessDefRegistry)
)
val FLOWS = component<AudioFlows>("flows", AudioFlows::createDefault)

val SERVER_OPTIONS = component<ServerOptions>("server_options", ServerOptions::default)
val OBJECTS = component<ScoreObjectRegistry>(
    "objects", ScoreObjectRegistry::createDefault,
    NamedObjectListSerializer(serializer(), ::ScoreObjectRegistry)
)
val SCORE = component<Score>("score", ::Score)

val allComponents = listOf<Component<out ContextualObject>>(
    GROUPS, BUSSES, BUFFERS,
    PATTERNS, SYNTH_DEFS, PROCESS_DEFS,
    UI_STATE,
    FLOWS, SERVER_OPTIONS,
    OBJECTS, SCORE
) + ScriptObject.Type.entries.map { type -> type.component }

inline operator fun <reified T : ContextualObject> XenakisProject.get(component: Component<out T>) =
    components[component] as T

val XenakisProject.score get() = get(SCORE)
val XenakisProject.busses get() = get(BUSSES)
val XenakisProject.buffers get() = get(BUFFERS)
val XenakisProject.patterns get() = get(PATTERNS)
val XenakisProject.instruments get() = get(SYNTH_DEFS)
val XenakisProject.objects get() = get(OBJECTS)
val XenakisProject.flows get() = get(FLOWS)
val XenakisProject.settings get() = get(UI_STATE)