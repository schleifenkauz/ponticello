package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.writeCode
import ponticello.model.instr.BusObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.player.ActiveAudioFlow
import ponticello.model.player.ActiveObject
import ponticello.model.player.LiveSynthUpdater
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.BusControl
import ponticello.sc.client.ScWriter
import ponticello.ui.midi.MidiContext
import ponticello.ui.midi.ParameterControlsMidiContext
import reaktive.value.ReactiveValue
import reaktive.value.now

@Serializable
sealed class ParameterizedAudioFlow : AudioFlow(), ParameterizedObject {
    override val superColliderPrefix: String
        get() = "~"

    @Transient
    private lateinit var listener: LiveSynthUpdater

    final override fun activeObjects(): List<ActiveObject> =
        if (isActive.now) listOf(ActiveAudioFlow(this)) else emptyList()

    final override fun duration(): ReactiveValue<Decimal>? = null

    override fun initialize(context: Context) {
        super.initialize(context)
        listener = LiveSynthUpdater(this)
    }

    override fun onLoadedIntoRegistry() {
        super<AudioFlow>.onAdded()
        listener.startListening()
    }

    override fun onRemoved() {
        super<AudioFlow>.onRemoved()
        listener.stopListening()
    }

    override fun ScWriter.createObject() {
        SoundProcess.createSoundProcessObject(
            writer, this@ParameterizedAudioFlow,
            duration = Decimal.INF, superColliderName
        )
    }

    override fun writeCode(placement: NodePlacement): String = writeCode {
        +"var inst = $superColliderName.createInstance"
        +"inst.start($placement, 0, -1)"
    }

    override fun midiContext(): MidiContext? = ParameterControlsMidiContext(controls)

    override fun usesBus(bus: BusObject): Boolean = controls.any { control ->
        val c = control.now
        c is BusControl && c.bus.now.get() == bus
    }
}