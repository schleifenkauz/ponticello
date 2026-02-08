package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.writeCode
import ponticello.model.instr.BusObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.player.SoundProcessUpdater
import ponticello.model.score.SoundProcess
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

    override fun deactivate() {
        super<AudioFlow>.deactivate()
        listener.stopListening()
    }

    override fun ScWriter.createObject() {
        SoundProcess.createSoundProcessObject(writer, this@ParameterizedAudioFlow, duration = null)
        isCreatedInSuperCollider = true
    }

    override fun onRename(oldName: String, newName: String) {
        client.run {
            +"SoundProcess.rename('${soundProcessName(oldName)}', '${soundProcessName(newName)}')"
            val oldSuperColliderName = superColliderName(oldName)
            +"$superColliderName = $oldSuperColliderName"
            +"$oldSuperColliderName = nil"
        }
    }

    override fun writeCode(placement: NodePlacement): String = writeCode {
        +"$superColliderName = SoundProcess.get('flow_${name.now}').createInstance(extra_args: (auto_release: 0))"
        +"$superColliderName.start($placement, 0, -1, ${isActive.now})"
    }

    override fun midiContext(): MidiContext? = ParameterControlsMidiContext(controls)

    override fun usesBus(bus: BusObject): Boolean = controls.any { control ->
        val c = control.now
        c is BusControl && c.bus.now.get() == bus
    }
}