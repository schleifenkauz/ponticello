package ponticello.model.midi

import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.asY
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.instr.MidiInstrument
import ponticello.model.obj.MidiTrackReference
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.MidiObject
import ponticello.model.score.Score
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ValueControl
import reaktive.value.reactiveVariable
import java.io.File
import java.nio.ByteBuffer
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

object MidiUtil {
    fun convertToMidiObject(track: MidiTrackReference, events: List<MidiEvent>, context: Context): MidiObject? {
        if (events.none { it.type == MidiEvent.Type.NoteOn }) return null
        val noteStart = mutableMapOf<Int, MidiEvent>()
        val notes = mutableListOf<ScoreObjectInstance>()
        var lowestPitch = 127
        var highestPitch = 0
        for (ev in events) {
            val (time, type, pitch, value) = ev
            when {
                type == MidiEvent.Type.NoteOff || type == MidiEvent.Type.NoteOn && value == 0 -> {
                    val startEv = noteStart.remove(pitch) ?: continue
                    val inst = createNote(startEv, endTime = time, context)
                    notes.add(inst)
                }

                type == MidiEvent.Type.NoteOn -> {
                    noteStart[pitch] = ev
                    lowestPitch = minOf(lowestPitch, pitch)
                    highestPitch = maxOf(highestPitch, pitch)
                }

                type == MidiEvent.Type.ControlChange -> {}
            }
        }
        val lastEventTime = events.last().time
        for (ev in noteStart.values) {
            val note = createNote(ev, endTime = lastEventTime, context)
            if (note.duration > zero) notes.add(note)
        }
        val score = Score(notes)
        val midiObject = MidiObject(
            reactiveVariable(track),
            lowestPitch, highestPitch,
            score, ParameterControlList()
        )
        midiObject.setInitialSize(events.last().time, height = 0.2.asY)
        return midiObject
    }

    private fun createNote(startEv: MidiEvent, endTime: Decimal, context: Context): ScoreObjectInstance {
        val duration = endTime - startEv.time
        val controls = ParameterControlList.create(
            "velocity" to ValueControl.create(startEv.value.toDecimal()),
            "channel" to ValueControl.create(startEv.channel.toDecimal())
        )
        val objectName = context[ScoreObjectRegistry].availableName("note")
        val obj = SoundProcess.create(objectName, MidiInstrument.reference(), controls)
        obj.setInitialSize(duration, height = 0.05.asY)
        val inst = ScoreObjectInstance(obj, startEv.time, startEv.num.toDecimal())
        return inst
    }

    fun createMidiObjectFromFile(file: File, track: MidiTrackReference, context: Context): MidiObject? {
        val sequence = MidiSystem.getSequence(file)
        val ticksPerBeat = sequence.resolution
        var beatsPerSecond = 1.0
        val tracks = sequence.tracks.map { track ->
            val events = mutableListOf<MidiEvent>()
            for (i in 0 until track.size()) {
                val midiEvent = track.get(i)
                val message = midiEvent.message
                if (message is MetaMessage && message.message[1] == 0x51.toByte()) {
                    val arr = ByteArray(4)
                    message.data.copyInto(arr, destinationOffset = 1)
                    val tempo = ByteBuffer.wrap(arr).getInt()
                    beatsPerSecond = 1000000.0 / tempo
                }
                if (message !is ShortMessage) continue
                val beat = midiEvent.tick.toDouble() / ticksPerBeat
                val time = (beat / beatsPerSecond).toDecimal()
                val command = when (message.command) {
                    ShortMessage.NOTE_ON -> MidiEvent.Type.NoteOn
                    ShortMessage.NOTE_OFF -> MidiEvent.Type.NoteOff
                    ShortMessage.CONTROL_CHANGE -> MidiEvent.Type.ControlChange
                    else -> continue
                }
                val ev = MidiEvent(time, command, message.data1, message.data2, message.channel)
                events.add(ev)
            }
            events
        }
        val bestTrack = tracks.maxBy { t -> t.count { ev -> ev.type == MidiEvent.Type.NoteOn } }
        return convertToMidiObject(track, bestTrack, context)
    }
}