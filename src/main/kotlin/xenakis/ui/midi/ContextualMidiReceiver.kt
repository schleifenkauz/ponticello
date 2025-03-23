package xenakis.ui.midi

import bundles.PublicProperty
import bundles.publicProperty
import xenakis.impl.MidiPitch
import javax.sound.midi.*

class ContextualMidiReceiver : Receiver {
    private var midiContext: MidiContext? = null
    private var device: MidiDevice? = null

    override fun send(message: MidiMessage?, timeStamp: Long) {
        if (message !is ShortMessage) return
        val ctx = midiContext ?: return
        when (message.command) {
            ShortMessage.NOTE_ON -> ctx.noteOn(message.channel, MidiPitch(message.data1), message.data2)
            ShortMessage.NOTE_OFF -> ctx.noteOff(message.channel, MidiPitch(message.data1))
            ShortMessage.CONTROL_CHANGE -> {
                val index = message.data1 - INDEX_OFFSET
                ctx.cc(message.channel, index, message.data2)
            }
        }
    }

    fun setContext(context: MidiContext?) {
        midiContext = context
    }

    fun initialize(deviceName: String) {
        val devices = MidiSystem.getMidiDeviceInfo()
        for (info in devices.filter { d -> d.name.startsWith(deviceName) }) {
            device = MidiSystem.getMidiDevice(info)
            try {
                device!!.open()
                device!!.transmitter.receiver = this
                break
            } catch (e: Exception) {
                System.err.println("Exception while attempting to open midi device ${info.name}: ${e.message}")
                continue
            }
        }
    }

    override fun close() {
        device?.close()
    }

    companion object : PublicProperty<ContextualMidiReceiver> by publicProperty("Midi receiver") {
        private const val INDEX_OFFSET = 20
    }
}