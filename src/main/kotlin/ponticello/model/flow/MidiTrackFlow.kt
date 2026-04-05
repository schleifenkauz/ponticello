package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.impl.toDecimal
import ponticello.impl.writeCode
import ponticello.model.midi.MidiDeviceSpec
import ponticello.model.midi.MidiInstrument
import ponticello.model.midi.MidiRecorder
import ponticello.model.player.ScorePlayer
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.client.ScWriter
import ponticello.sc.client.run
import reaktive.Observer
import reaktive.value.*

@Serializable
class MidiTrackFlow(
    val sourceDevice: ReactiveVariable<MidiDeviceSpec> = reactiveVariable(MidiDeviceSpec.none()),
    val instruments: InstrumentList = InstrumentList(),
) : AudioFlow() {
    override val active = reactiveVariable(true)

    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    private var sourceDeviceObserver: Observer? = null

    @Transient
    private val recording = reactiveVariable(false)

    val isRecording: ReactiveValue<Boolean> get() = recording

    override val isValid: ReactiveValue<Boolean>
        get() = reactiveValue(true)

    override fun initialize(context: Context) {
        super.initialize(context)
        instruments.initialize(this)
    }

    override fun activate() {
        sourceDeviceObserver = sourceDevice.observe { _, _, newSource ->
            client.run("$superColliderName.sourceDevice = ${newSource.code}")
        }
    }

    override fun onRemoved() {
        sourceDeviceObserver?.kill()
        sourceDeviceObserver = null
    }

    override fun writeCode(): String = writeCode {
        for (instr in instruments) {
            append(instr.superColliderName, " = ")
            instr.run { createInstrument(this@MidiTrackFlow) }
            appendLine(";")
        }
        appendLine("MidiTrack('${name.now}', ${sourceDevice.now.code}, [")
        indented {
            for (instr in instruments) {
                append(instr.superColliderName)
                appendLine(",")
            }
        }
        append("])")
    }

    fun toggleRecording() {
        if (isRecording.now) {
            context[MidiRecorder].finishRecording(this)
            recording.now = false
        } else {
            context[MidiRecorder].startRecording(this)
            recording.now = true
        }
    }

    fun allNotesOff(playerId: Int) {
        client.run("$superColliderName.allNotesOff(player_id: $playerId)")
    }

    override fun referencesScoreObject(obj: ScoreObject): Boolean =
        instruments.any { instr -> instr.referencesScoreObject(obj) }

    override fun copy(): AudioFlow = MidiTrackFlow(sourceDevice.copy(), instruments.copy())

    override fun ScWriter.createObject() {}

    fun ScWriter.sendNoteOn(
        midinote: Decimal, controls: ParameterControlList,
        latency: Decimal, player: ScorePlayer
    ) {
        val velocity = controls.getOrNull("velocity")?.now?.getNumericalValue() ?: 64.toDecimal()
        val channel = controls.getOrNull("channel")?.now?.getNumericalValue() ?: 0
        val src = "(latency: $latency, player_id: ${player.id})"
        append(superColliderName, ".noteOn(", midinote, ",", velocity, ",", channel, ",", src, ")")
        appendLine(";")
    }

    fun ScWriter.sendNoteOff(midinote: Decimal, controls: ParameterControlList, latency: Decimal) {
        val channel = controls.getOrNull("channel")?.now?.getNumericalValue() ?: 0
        val velocity = "0"
        val src = "(latency: $latency)"
        append(superColliderName, ".noteOff(", midinote, ",", velocity, ",", channel, ",", src, ")")
        appendLine(";")
    }

    @Serializable(with = InstrumentList.Serializer::class)
    class InstrumentList(
        override val objects: MutableList<MidiInstrument> = mutableListOf()
    ) : NamedObjectList<MidiInstrument>() {
        @Transient
        private lateinit var track: MidiTrackFlow

        override val objectType: String
            get() = "MIDI Instrument"

        fun initialize(track: MidiTrackFlow) {
            this.track = track
            super.initialize(track.context)
        }

        override fun onAdded(obj: MidiInstrument, idx: Int) {
            track.client.run {
                append(obj.superColliderName, " = ")
                obj.run { createInstrument(track) }
                appendLine(";")
                +"${track.superColliderName}.insertInstrument($idx, ${obj.superColliderName})"
            }
        }

        override fun onRemoved(obj: MidiInstrument, idx: Int) {
            track.client.run { +"${track.superColliderName}.removeInstrument($idx)" }
        }

        override fun onMoved(obj: MidiInstrument, oldIdx: Int, newIdx: Int) {
            track.client.run("${track.superColliderName}.instruments.move($oldIdx, $newIdx)")
        }

        fun copy() = InstrumentList(objects.mapTo(mutableListOf()) { instr -> instr.copy() })

        object Serializer : ObjectListSerializer<MidiInstrument, InstrumentList>(
            MidiInstrument.Companion.serializer(), ::InstrumentList
        )
    }
}