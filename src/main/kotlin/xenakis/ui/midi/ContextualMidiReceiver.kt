package xenakis.ui.midi

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import xenakis.impl.MidiPitch
import xenakis.model.obj.AbstractContextualObject
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage

class ContextualMidiReceiver : Receiver, AbstractContextualObject() {
    private var midiContext: MidiContext? = null

    override fun send(message: MidiMessage?, timeStamp: Long) {
        if (message !is ShortMessage) return
        val ctx = midiContext ?: return
        when (message.command) {
            ShortMessage.NOTE_ON -> ctx.noteOn(message.channel, MidiPitch(message.data1), message.data2)
            ShortMessage.NOTE_OFF -> ctx.noteOff(message.channel, MidiPitch(message.data1))
            ShortMessage.CONTROL_CHANGE -> ctx.cc(message.channel, message.data1, message.data2)
        }
    }

    fun setContext(context: MidiContext) {
        midiContext = context
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        context[ContextualMidiReceiver] = this
        val devices = MidiSystem.getMidiDeviceInfo()
        for (info in devices.filter { d -> d.name.startsWith("Xjam") }) {
            val device = MidiSystem.getMidiDevice(info)
            try {
                device.open()
                device.transmitter.receiver = this
                break
            } catch (e: Exception) {
                continue
            }
        }

    }

    override fun close() {

    }

    companion object : PublicProperty<ContextualMidiReceiver> by publicProperty("Midi receiver")
}