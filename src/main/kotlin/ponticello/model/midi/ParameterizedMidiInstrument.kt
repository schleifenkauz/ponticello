package ponticello.model.midi

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.NoInstrument
import ponticello.model.instr.ParameterizedObject
import ponticello.model.player.SoundProcessUpdater
import ponticello.model.registry.ObjectReference
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.ui.midi.MidiContext
import ponticello.ui.midi.ParameterControlsMidiContext
import reaktive.Reactive
import reaktive.value.ReactiveValue
import reaktive.value.now

@Serializable
sealed class ParameterizedMidiInstrument : MidiInstrument(), ParameterizedObject {
    @SerialName("instrument")
    abstract val reference: ReactiveValue<ObjectReference<out InstrumentObject>>

    @Transient
    private lateinit var updater: SoundProcessUpdater<*>

    @Transient
    final override var isCreatedInSuperCollider: Boolean = false
        private set

    override val instrumentChanged: Reactive
        get() = reference

    override fun getInstrument(): InstrumentObject = reference.now.get() ?: NoInstrument()

    override fun initialize(context: Context) {
        super.initialize(context)
        resolveInstrument()
        controls.initialize(context, this)
    }

    protected fun setupSoundProcessUpdater() {
        isCreatedInSuperCollider = true
        updater = SoundProcessUpdater(this)
        updater.startListening()
    }

    protected abstract fun resolveInstrument()

    override fun toString(): String {
        val instrName = reference.now.getName()
        return "SoundProcessMidiInstrument <$instrName>"
    }

    override fun onRemoved() {
        super<MidiInstrument>.onRemoved()
        updater.stopListening()
        context[SuperColliderClient].run {
            +"SoundProcess.remove('${name.now}')"
        }
    }

    override fun midiContext(): MidiContext = ParameterControlsMidiContext(controls)
}