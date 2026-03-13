package ponticello.ui.midi

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.NoInstrument
import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.project
import ponticello.model.player.SoundProcessUpdater
import ponticello.model.project.instruments
import ponticello.model.registry.ObjectReference
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import reaktive.Reactive
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
@SerialName("SoundProcessMidiInstrument")
class SoundProcessMidiInstrument(
    @SerialName("instrument")
    val reference: ReactiveVariable<ObjectReference<InstrumentObject>>,
    override val controls: ParameterControlList
) : MidiInstrument(), ParameterizedObject {

    @Transient
    private lateinit var updater: SoundProcessUpdater<SoundProcessMidiInstrument>

    override val instrumentChanged: Reactive
        get() = reference

    override fun soundProcessName(objectName: String): String = objectName

    override fun getInstrument(): InstrumentObject = reference.now.get() ?: NoInstrument()

    override var isCreatedInSuperCollider: Boolean = false
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        reference.now.resolve(context.project.instruments)
        controls.initialize(context, this)
    }

    override fun toString(): String {
        val instrName = reference.now.getName()
        return "SoundProcessMidiInstrument <$instrName>"
    }

    override fun activate() {
        context[SuperColliderClient].run {
            SoundProcess.createSoundProcessObject(writer, this@SoundProcessMidiInstrument, duration = null)
            appendLine(";")
            +"$superColliderName = SoundProcessMidiInstrument('$soundProcessName', enabled: ${isEnabled.now})"
        }
        super<MidiInstrument>.activate()
        isCreatedInSuperCollider = true
        updater = SoundProcessUpdater(this)
        updater.startListening()
    }

    override fun onRemoved() {
        super<MidiInstrument>.onRemoved()
        updater.stopListening()
        context[SuperColliderClient].run("SoundProcess.remove('${name.now}')")
    }

    override fun copy(): MidiInstrument = SoundProcessMidiInstrument(reference.copy(), controls.copy())
}