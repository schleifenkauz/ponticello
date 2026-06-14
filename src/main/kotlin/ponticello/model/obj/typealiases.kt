package ponticello.model.obj

import hextant.context.Context
import kotlinx.serialization.Contextual
import ponticello.model.code.GlobalPatternObject
import ponticello.model.code.ScriptObject
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.instr.*
import ponticello.model.live.LiveObject
import ponticello.model.live.LiveTaskObject
import ponticello.model.midi.MidiInstrument
import ponticello.model.player.ClockObject
import ponticello.model.project.flows
import ponticello.model.record.LiveBufferObject
import ponticello.model.registry.ObjectReference
import ponticello.model.score.MeterObject
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.NamedParameterControl
import ponticello.model.server.BufferObject

typealias InstrumentReference = ObjectReference<@Contextual InstrumentObject>
typealias SynthDefReference = ObjectReference<@Contextual SynthDefObject>
typealias RoutineDefReference = ObjectReference<@Contextual RoutineDefObject>
typealias MidiEffectInstrumentReference = ObjectReference<@Contextual MidiEffectInstrument>
typealias MidiInstrumentReference = ObjectReference<@Contextual MidiInstrument>
typealias MidiTrackReference = ObjectReference<@Contextual MidiTrackFlow>
typealias BusReference = ObjectReference<@Contextual BusObject>
typealias BufferReference = ObjectReference<@Contextual BufferObject>
typealias GlobalPatternReference = ObjectReference<@Contextual GlobalPatternObject>
typealias ScoreObjectReference = ObjectReference<@Contextual ScoreObject>
typealias MeterReference = ObjectReference<@Contextual MeterObject>
typealias ClockReference = ObjectReference<@Contextual ClockObject>
typealias ParameterDefReference = ObjectReference<@Contextual ParameterDefObject>
typealias FlowGroupReference = ObjectReference<@Contextual AudioFlowGroup>
typealias ScriptObjectReference = ObjectReference<@Contextual ScriptObject>
typealias LiveObjectReference = ObjectReference<@Contextual LiveObject>
typealias LiveTaskReference = ObjectReference<@Contextual LiveTaskObject>
typealias FlowReference = ObjectReference<@Contextual AudioFlow>
typealias ParameterControlReference = ObjectReference<@Contextual NamedParameterControl>
typealias VSTPluginReference = ObjectReference<@Contextual VSTPluginFlow>
typealias LiveBufferReference = ObjectReference<@Contextual LiveBufferObject>

fun FlowReference.resolve(context: Context): AudioFlow? {
    val allFlows = context.project.flows.allFlows()
    return resolve(allFlows)
}

fun MidiInstrumentReference.resolve(context: Context): MidiInstrument? {
    val allMidiInstruments = context.project.flows.allMidiTracks().flatMap(MidiTrackFlow::instruments)
    return resolve(allMidiInstruments)
}