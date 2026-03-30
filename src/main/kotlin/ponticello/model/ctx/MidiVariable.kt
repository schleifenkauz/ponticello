package ponticello.model.ctx

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

data class MidiVariable(val controlName: String, val type: String) : BoundVariable() {
    override val name: ReactiveString get() = reactiveValue(controlName)

    override val origin: Any get() = name

    override val info: ReactiveString get() = reactiveValue(type)

    override val icon: Ikon get() = MaterialDesignM.MIDI_PORT

    companion object {
        val PITCH = MidiVariable("pitch", "MIDI")
        val VELOCITY = MidiVariable("velocity", "MIDI")
    }
}