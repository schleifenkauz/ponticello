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

abstract class OSCMidiListener : OSCMessageListener, AbstractContextualObject() {
    private val attached = reactiveVariable(false)

    val isAttached: ReactiveBoolean get() = attached

    private lateinit var client: SuperColliderClient

    private val superColliderVar = "~midi_forward_${idCounter++}"

    override fun initialize(context: Context) {
        super.initialize(context)
        client = context[SuperColliderClient]
        client.addListener(this)
        client.run("$superColliderVar = OSCMidiForward.new")
    }

    fun attachTo(device: MidiDeviceSpec) {
        attached.now = try {
            client.eval(
                "$superColliderVar.attachTo(${device.code})"
            ).get().toBooleanStrictOrNull() ?: false
        } catch (e: Exception) {
            Logger.error("Failed to attach to MIDI device $device", e, Logger.Category.Midi)
            false
        }
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        val address = event.message.address
        try {
            when (address) {
                "/forward_note_on" -> {
                    val num = event.message.getArgument<Int>(0, "num") ?: return
                    val vel = event.message.getArgument<Int>(1, "vel") ?: return
                    noteOn(num, vel)
                }

                "/forward_note_off" -> {
                    val num = event.message.getArgument<Int>(0, "num") ?: return
                    val vel = event.message.getArgument<Int>(1, "vel") ?: return
                    noteOff(num, vel)
                }

                "/forward_cc" -> {
                    val num = event.message.getArgument<Int>(0, "num") ?: return
                    val vel = event.message.getArgument<Int>(1, "vel") ?: return
                    controlChange(num, vel)
                }
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
    }
}