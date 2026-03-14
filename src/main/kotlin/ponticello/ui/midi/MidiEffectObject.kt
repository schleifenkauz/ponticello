package ponticello.ui.midi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.flow.NodePlacement
import ponticello.model.instr.MidiEffectInstrument
import ponticello.model.obj.MidiEffectInstrumentReference
import ponticello.model.obj.project
import ponticello.model.project.instruments
import ponticello.model.score.SoundProcess.Companion.createSoundProcessObject
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
@SerialName("MidiEffect")
class MidiEffectObject(
    @SerialName("instrument")
    override val reference: ReactiveVariable<MidiEffectInstrumentReference>,
    override val controls: ParameterControlList
) : ParameterizedMidiInstrument() {
    override fun resolveInstrument() {
        val midiEffectInstruments = context.project.instruments.filterIsInstance<MidiEffectInstrument>()
        reference.now.resolve(midiEffectInstruments)
    }

    override fun getInstrument(): MidiEffectInstrument =
        reference.now.get() ?: error("Instrument ${reference.now.getName()} not found")

    override fun soundProcessName(objectName: String): String = "midi_effect_$objectName"

    override fun duration(): ReactiveValue<Decimal>? = null

    override fun addToTrack(writer: ScWriter, track: MidiTrackFlow, placement: NodePlacement) {
        writer.append("$superColliderName = ")
        writer.createSoundProcessObject(
            this,
            className = "MidiEffect",
            extraArguments = listOf("enabled: ${isEnabled.now}")
        )
        writer.appendLine(";")
        super.addToTrack(writer, track, placement)
    }

    override fun copy(): MidiInstrument = MidiEffectObject(reference.copy(), controls.copy())

    override fun midiContext(): MidiContext = ParameterControlsMidiContext(controls)
}