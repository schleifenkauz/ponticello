package ponticello.ui.midi

import javafx.application.Platform
import ponticello.impl.Decimal
import ponticello.impl.withPrecision
import ponticello.model.flow.MixerFlow
import ponticello.model.flow.MixerFlow.MixerComponentMode.*
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import reaktive.value.now
import javax.sound.midi.*

class AkaiMidiMix : Receiver {
    private var device: MidiDevice? = null
    var flow: MixerFlow? = null

    fun detach() {
        device?.transmitter?.receiver = null
        device = null
    }

    fun attachTo(deviceInfo: MidiDevice.Info) {
        val device = MidiSystem.getMidiDevice(deviceInfo)
        if (!device.isOpen) device.open()
        device.transmitter.receiver = this
        this.device = device
    }

    override fun send(message: MidiMessage, timeStamp: Long) {
        if (message !is ShortMessage) return
        val flow = flow ?: return
        println(message.message.toList())
        when (message.command) {
            ShortMessage.CONTROL_CHANGE -> {
                val remainder = message.data1 % 4
                val channel = (message.data1 - 16) / 4
                val component = flow.components.getOrNull(channel)
                when {
                    message.data1 == 62 -> {
                        Platform.runLater { flow.masterVolume.now = getVolume(message.data2) }
                    }

                    remainder == 0 -> {} //TODO some filter
                    remainder == 1 -> {} //TODO some filter
                    remainder == 2 -> {
                        val panVar = component?.pan ?: return
                        Platform.runLater { panVar.now = getPan(message.data2) }
                    }

                    remainder == 3 -> {
                        val volumeVar = component?.volume ?: return
                        Platform.runLater { volumeVar.now = getVolume(message.data2) }
                    }
                }

            }

            ShortMessage.NOTE_ON -> {
                val remainder = message.data1 % 3
                val channel = (message.data1 - 1) / 3
                val component = flow.components.getOrNull(channel) ?: return
                when (remainder) {
                    0 -> component.state.now = if (component.state.now == Mute) Regular else Mute
                    1 -> component.state.now = if (component.state.now == Solo) Regular else Solo
                    2 -> flow.context.project[UI_STATE].selectedAudioBus = component.sourceBus
                }
            }
        }
    }

    private fun getVolume(byte: Int): Decimal {
        val scaled = byte / 127.0 * 60.0
        return (scaled - 60.0).withPrecision(1)
    }

    private fun getPan(byte: Int): Decimal {
        val scaled = byte / 127.0 * 200
        return (scaled - 100.0).withPrecision(0)
    }

    override fun close() {
    }
}