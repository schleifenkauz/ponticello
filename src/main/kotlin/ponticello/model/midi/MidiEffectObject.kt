package ponticello.model.midi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.instr.MidiEffectInstrument
import ponticello.model.obj.MidiEffectInstrumentReference
import ponticello.model.obj.project
import ponticello.model.project.instruments
import ponticello.model.score.SoundProcess.Companion.createSoundProcessObject
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.client.ScWriter
import ponticello.ui.midi.MidiContext
import ponticello.ui.midi.ParameterControlsMidiContext
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

    override fun ScWriter.createInstrument(track: MidiTrackFlow) {
        createSoundProcessObject(
            this@MidiEffectObject,
            className = "MidiEffect",
            extraArguments = listOf("enabled: ${isEnabled.now}")
        )
        super.setupSoundProcessUpdater()
    }

    override fun copy(): MidiInstrument = MidiEffectObject(reference.copy(), controls.copy())

    override fun midiContext(): MidiContext = ParameterControlsMidiContext(controls)
}