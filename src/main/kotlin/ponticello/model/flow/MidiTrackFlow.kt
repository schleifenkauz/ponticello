package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.impl.writeCode
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectListSerializer
import ponticello.sc.client.ScWriter
import ponticello.sc.client.run
import ponticello.ui.midi.MidiDeviceSpec
import ponticello.ui.midi.MidiInstrument
import reaktive.Observer
import reaktive.value.*

@Serializable
class MidiTrackFlow(
    val sourceDevice: ReactiveVariable<MidiDeviceSpec> = reactiveVariable(MidiDeviceSpec.none()),
    val instruments: InstrumentList = InstrumentList(),
) : AudioFlow() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    private var sourceDeviceObserver: Observer? = null

    private val trackVariable get() = "~track_${name.now}"

    override val isValid: ReactiveValue<Boolean>
        get() = reactiveValue(true)

    override fun initialize(context: Context) {
        super.initialize(context)
        instruments.initialize(this)
    }

    override fun activate() {
        sourceDeviceObserver = sourceDevice.observe { _, _, newSource ->
            client.run("$trackVariable.sourceDevice = ${newSource.code}")
        }
    }

    override fun onRemoved() {
        sourceDeviceObserver?.kill()
        sourceDeviceObserver = null
    }

    override fun onRename(oldName: String, newName: String) {
        super.onRename(oldName, newName)
        client.run {
            +"~track_$newName = ~track_$oldName"
            +"~track_$oldName = nil"
        }
    }

    override fun writeCode(placement: NodePlacement): String = writeCode {
        for (instr in instruments) {
            val placement = NodePlacement.tail(superColliderName)
            instr.addToTrack(writer, this@MidiTrackFlow, placement)
        }
        appendLine("$trackVariable = MidiTrack(${sourceDevice.now.code}, [")
        indented {
            for (instr in instruments) {
                append(instr.superColliderName)
                appendLine(",")
            }
        }
        appendLine("]);")
        +"$superColliderName = $trackVariable.addToServer($placement)"
    }

    override fun copy(): AudioFlow = MidiTrackFlow(sourceDevice.copy(), instruments.copy())

    override fun ScWriter.createObject() {}

    override fun onRelease() {
        client.run {
            +"$trackVariable.release"
            +"$trackVariable = nil"
        }
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
                obj.addToTrack(writer, track, NodePlacement.tail(track.superColliderName))
                +"${track.trackVariable}.insertInstrument($idx, ${obj.superColliderName})"
            }
        }

        override fun onRemoved(obj: MidiInstrument, idx: Int) {
            track.client.run { +"${track.trackVariable}.removeInstrument($idx)" }
        }

        override fun onMoved(obj: MidiInstrument, oldIdx: Int, newIdx: Int) {
            track.client.run("${track.trackVariable}.instruments.move($oldIdx, $newIdx)")
        }

        fun copy() = InstrumentList(objects.mapTo(mutableListOf()) { instr -> instr.copy() })

        object Serializer : ObjectListSerializer<MidiInstrument, InstrumentList>(
            MidiInstrument.Companion.serializer(), ::InstrumentList
        )
    }
}