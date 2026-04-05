package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.writeCode
import ponticello.model.instr.BusObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.instr.ParameterizedObjectReference
import ponticello.model.player.SoundProcessUpdater
import ponticello.model.registry.reference
import ponticello.model.score.SoundProcess.Companion.createSoundProcessObject
import ponticello.model.score.controls.BusControl
import ponticello.sc.client.ScWriter
import ponticello.sc.client.run
import ponticello.ui.midi.MidiContext
import ponticello.ui.midi.ParameterControlsMidiContext
import reaktive.value.ReactiveValue
import reaktive.value.now

@Serializable
sealed class ParameterizedAudioFlow : AudioFlow(), ParameterizedObject {
    @Transient
    private lateinit var listener: SoundProcessUpdater<ParameterizedAudioFlow>

    override fun soundProcessName(objectName: String): String? = "flow_${objectName}"

    @Transient
    final override var isCreatedInSuperCollider: Boolean = false
        private set

    final override fun duration(): ReactiveValue<Decimal>? = null

    override fun initialize(context: Context) {
        super.initialize(context)
        listener = SoundProcessUpdater(this)
    }

    override fun activate() {
        super<AudioFlow>.onAdded()
        listener.startListening()
    }

    override fun onRemoved() {
        super<AudioFlow>.onRemoved()
        listener.stopListening()
    }

    override fun ScWriter.createObject() {
        isCreatedInSuperCollider = true
    }

    override fun onRename(oldName: String, newName: String) {
        client.run {
            +"SoundProcess.rename('${soundProcessName(oldName)}', '${soundProcessName(newName)}')"
        }
        super.onRename(oldName, newName)
    }

    override fun writeCode(): String = writeCode(group = false) {
        append("SoundProcessFlow('", name.now, "', ")
        writer.createSoundProcessObject(this@ParameterizedAudioFlow)
        append(")")
    }

    override fun midiContext(): MidiContext? = ParameterControlsMidiContext(controls)

    override fun usesBus(bus: BusObject): Boolean = controls.any { control ->
        val c = control.now
        c is BusControl && c.bus.now.get() == bus
    }

    override fun makeReference(): ParameterizedObjectReference? = ParameterizedObjectReference.Flow(reference())
}