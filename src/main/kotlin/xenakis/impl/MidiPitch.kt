package xenakis.impl

import kotlin.math.pow

class MidiPitch(val step: Int) {
    fun getNoteName(): String {
        val octave = step / 12
        val pitchClass = step % 12
        val noteName = NOTE_NAMES[pitchClass]
        return "$noteName$octave"
    }

    fun isBlackKey() = NOTE_NAMES[step % 12].contains('#')

    override fun toString(): String = getNoteName().removeSuffix("0")

    companion object {
        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        fun allPitchClasses() = (0..11).map { step -> MidiPitch(step) }

        fun toFreq(midinote: Int) = 440 * 2.0.pow((midinote - 69) / 12.0)
    }
}