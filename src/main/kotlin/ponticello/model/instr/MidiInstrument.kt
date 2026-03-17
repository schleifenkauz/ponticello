package ponticello.model.instr

import javafx.scene.paint.Color
import ponticello.model.obj.AbstractNamedObject
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveValue
import reaktive.value.reactiveValue

object MidiInstrument : InstrumentObject, AbstractNamedObject() {
    override val color: ReactiveValue<Color>
        get() = reactiveValue(Color.BLACK)
    override val instrumentType: String
        get() = "MIDI Instrument"
    override val parameters: List<ParameterDefObject>
        get() = listOf(ParameterDefObject.VELOCITY, ParameterDefObject.CHANNEL)
    override val superColliderName: String
        get() = "MIDIInstrument.new"

    override val name: ReactiveValue<String>
        get() = reactiveValue("<midi instrument>")

    override fun ScWriter.createObject() {}

    override fun ScWriter.freeObject() {}

    override fun sync() {}
}