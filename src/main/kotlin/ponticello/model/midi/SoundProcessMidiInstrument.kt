package ponticello.model.midi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.instr.InstrumentObject
import ponticello.model.obj.project
import ponticello.model.project.instruments
import ponticello.model.registry.ObjectReference
import ponticello.model.score.SoundProcess.Companion.createSoundProcessObject
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
@SerialName("SoundProcessMidiInstrument")
class SoundProcessMidiInstrument(
    @SerialName("instrument")
    override val reference: ReactiveVariable<ObjectReference<InstrumentObject>>,
    override val controls: ParameterControlList
) : ParameterizedMidiInstrument() {
    override fun resolveInstrument() {
        reference.now.resolve(context.project.instruments)
    }

    override fun soundProcessName(objectName: String): String = objectName

    override fun duration(): ReactiveValue<Decimal>? = null

    override fun ScWriter.createInstrument(track: MidiTrackFlow) {
        createSoundProcessObject(
            this@SoundProcessMidiInstrument,
            className = "SoundProcessMidiInstrument",
            extraArguments = listOf("enabled: ${isEnabled.now}")
        )
        super.setupSoundProcessUpdater()
    }

    override fun copy(): MidiInstrument = SoundProcessMidiInstrument(reference.copy(), controls.copy())

}