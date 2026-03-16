package ponticello.model.midi

import ponticello.impl.Decimal

data class MidiEvent(val time: Decimal, val type: Type, val num: Int, val value: Int, val channel: Int) {
    enum class Type {
        NoteOff, NoteOn, ControlChange;
    }
}