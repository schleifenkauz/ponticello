package ponticello.ui.midi

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.midi.MidiDeviceSpec
import ponticello.model.obj.AbstractContextualObject
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.client.getArgument
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveVariable

abstract class OSCMidiListener(private val name: String) : OSCMessageListener, AbstractContextualObject() {
    private val attached = reactiveVariable(false)

    val isAttached: ReactiveBoolean get() = attached

    private lateinit var client: SuperColliderClient

    private val superColliderVar = "~midi_forward_${name}"

    private var sourceDevice: String? = "nil"

    override fun initialize(context: Context) {
        super.initialize(context)
        client = context[SuperColliderClient]
        client.addListener(this)
        client.run("$superColliderVar = OSCMidiForward('midi_forward_$name')")
    }

    fun attachTo(device: MidiDeviceSpec) {
        sourceDevice = device.code
        attached.now = try {
            client.eval("$superColliderVar.attachTo($sourceDevice)")
                .get().toBooleanStrictOrNull() ?: false
        } catch (e: Exception) {
            Logger.error("Failed to attach to MIDI device $device", e, Logger.Category.Midi)
            false
        }
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        val address = event.message.address
        if (address !in ACCEPTED_MESSAGES) return
        val sourceDevice = event.message.getArgument<String>(0, "sourceDevice") ?: return
        if (sourceDevice != this.sourceDevice?.removeSurrounding("\"")) return
        val num = event.message.getArgument<Int>(1, "num") ?: return
        val vel = event.message.getArgument<Int>(2, "vel") ?: return
        try {
            when (address) {
                "/forward_note_on" -> noteOn(num, vel)
                "/forward_note_off" -> noteOff(num, vel)
                "/forward_cc" -> controlChange(num, vel)
            }
        } catch (e: Exception) {
            Logger.error("Exception while processing MIDI message from $address", e, Logger.Category.Midi)
        }
    }

    protected open fun noteOn(num: Int, velocity: Int) {}

    protected open fun noteOff(num: Int, velocity: Int) {}

    protected open fun controlChange(index: Int, value: Int) {}

    companion object {
        private var idCounter = 0
        private val ACCEPTED_MESSAGES = setOf("/forward_note_on", "/forward_note_off", "/forward_cc")
    }
}