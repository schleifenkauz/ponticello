package ponticello.ui.midi

import ponticello.impl.MidiPitch

interface MidiContext {
    fun cc(channel: Int, index: Int, value: Int) {}

    fun noteOn(channel: Int, midiPitch: MidiPitch, velocity: Int) {}

    fun noteOff(channel: Int, midiPitch: MidiPitch) {}
}