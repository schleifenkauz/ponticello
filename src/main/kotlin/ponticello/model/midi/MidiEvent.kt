package ponticello.model.midi

import kotlinx.serialization.Serializable
import ponticello.impl.Decimal

@Serializable
data class MidiEvent(val time: Decimal, val type: Type, val num: Int, val value: Int, val channel: Int) {
    enum class Type {
        NoteOff, NoteOn, ControlChange;
    }
}