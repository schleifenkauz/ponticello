package ponticello.model.midi

import bundles.PublicProperty
import bundles.publicProperty
import com.illposed.osc.OSCMessage
import hextant.context.Context
import javafx.application.Platform
import ponticello.impl.Logger
import ponticello.impl.toDecimal
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import ponticello.ui.score.ScoreObjectDuplicator
import reaktive.event.event

class MidiRecorder(private val context: Context) {
    private val client: SuperColliderClient = context[SuperColliderClient]
    private val tracksByRecordingId = mutableMapOf<Int, MidiTrackFlow>()

    private val recordingFinish = event<List<MidiEvent>>()

    val recordingFinished get() = recordingFinish.stream

    init {
        client.addListener("/midi_recording_finished") { _, msg -> recordingFinished(msg) }
    }

    private fun recordingFinished(msg: OSCMessage) {
        val recordingId = msg.getArgument<Int>(0, "Recording ID") ?: return
        val track = tracksByRecordingId.remove(recordingId)
        if (track == null) {
            Logger.warn("Received /midi_recording_finished for unknown recording ID: $recordingId", Logger.Category.OSC)
            return
        }
        val nEvents = msg.getArgument<Int>(1, "Number of MIDI events") ?: return
        val eventList = msg.arguments.drop(2)
        require(eventList.size == nEvents * 5) { "Invalid eventList size: ${eventList.size}, expected: ${nEvents * 5}" }
        val events = eventList.chunked(5).map { ev ->
            val time = (ev[0] as Float).toDecimal()
            val type = MidiEvent.Type.entries[ev[1] as Int]
            val pitch = ev[2] as Int
            val velocity = ev[3] as Int
            val channel = ev[4] as Int
            MidiEvent(time, type, pitch, velocity, channel)
        }
        recordingFinish.fire(events)
        enterDuplicateMode(track, events)
    }

    private fun enterDuplicateMode(track: MidiTrackFlow, events: List<MidiEvent>) {
        val midiObject = MidiUtil.convertToMidiObject(track.reference(), events, context) ?: return
        val name = context[ScoreObjectRegistry].availableName("midi")
        midiObject.setInitialName(name)
        midiObject.initialize(context)
        Platform.runLater {
            context[ScoreObjectDuplicator].enterDuplicateMode(midiObject)
        }
    }

    fun startRecording(track: MidiTrackFlow) {
        val recordingId = recordingIdCounter++
        tracksByRecordingId[recordingId] = track
        client.run("${track.superColliderName}.recorder.startRecording($recordingId)")
    }

    fun finishRecording(track: MidiTrackFlow) {
        client.run("${track.superColliderName}.recorder.finishRecording")
    }

    companion object : PublicProperty<MidiRecorder> by publicProperty("MidiRecorder") {
        private var recordingIdCounter = 0
    }
}