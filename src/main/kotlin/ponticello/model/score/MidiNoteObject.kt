package ponticello.model.score

import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.MidiInstrument
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.ControlSpec
import reaktive.Reactive
import reaktive.event.never
import reaktive.value.ReactiveVariable

class MidiNoteObject(override val controls: ParameterControlList) : ScoreObject(), ParameterizedObject {
    override val type: String
        get() = "MIDI note"

    override var _name: ReactiveVariable<String>? = null

    override fun getInstrument(): InstrumentObject = MidiInstrument

    override val instrumentChanged: Reactive get() = never<Unit>()

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override fun doClone(): ScoreObject = MidiNoteObject(controls.copy())

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)
}