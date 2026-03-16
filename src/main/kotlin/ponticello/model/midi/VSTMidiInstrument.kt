package ponticello.model.midi

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.flow.NodePlacement
import ponticello.model.flow.VSTPluginFlow
import ponticello.sc.client.ScWriter
import ponticello.sc.client.run
import reaktive.value.now

@SerialName("VSTMidiInstrument")
@Serializable
class VSTMidiInstrument(
    val flow: VSTPluginFlow,
) : MidiInstrument() {
    val vst: VSTPluginFlow get() = flow

    override fun initialize(context: Context) {
        super.initialize(context)
        flow.setInitialName(this.name.now)
        flow.initialize(context)
    }

    override fun toString(): String {
        val flowName = flow.name.now
        return "VSTMidiInstrument <$flowName>"
    }

    override fun setEnabled(value: Boolean) {
        super.setEnabled(value)
        flow.setActive(value)
    }

    override fun addToTrack(writer: ScWriter, track: MidiTrackFlow, placement: NodePlacement) {
        flow.setFlowGroup(track.parentGroup!!)
        writer.run {
            append(flow.writeCode(placement))
            appendLine(";")
            +"$superColliderName = VSTMidiInstrument(${vst.controllerVar}, enabled: ${isEnabled.now})"
        }
    }

    override fun onRemoved() {
        super.onRemoved()
        flow.release()
    }

    override fun copy(): MidiInstrument = VSTMidiInstrument(flow)

    companion object
}