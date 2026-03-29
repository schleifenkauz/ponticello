package ponticello.model.ctx

import bundles.PublicProperty
import bundles.publicProperty
import ponticello.model.code.GlobalPatternObject
import ponticello.model.code.OSCHookObject
import ponticello.model.code.ScriptObject
import ponticello.model.instr.ConfigurableInstrumentObject
import ponticello.model.instr.CustomizableSynthDefObject
import ponticello.model.instr.MidiEffectInstrument
import ponticello.model.instr.RoutineDefObject
import ponticello.model.score.TaskObject
import ponticello.model.score.controls.ParameterControlList

sealed class PonticelloContext {
    sealed class InstrumentDef : PonticelloContext() {
        abstract val def: ConfigurableInstrumentObject
    }

    data class SynthDef(override val def: CustomizableSynthDefObject) : InstrumentDef()

    data class RoutineDef(override val def: RoutineDefObject) : InstrumentDef()

    data class MidiEffect(override val def: MidiEffectInstrument) : InstrumentDef()

    data class Control(val control: ParameterControlList.NamedParameterControl) : PonticelloContext()

    data class GlobalPattern(val pattern: GlobalPatternObject) : PonticelloContext()

    data class Script(val script: ScriptObject) : PonticelloContext()

    data class Task(val task: TaskObject) : PonticelloContext()

    data class OSCHook(val hook: OSCHookObject) : PonticelloContext()

    companion object : PublicProperty<PonticelloContext> by publicProperty("PonticelloContext")
}