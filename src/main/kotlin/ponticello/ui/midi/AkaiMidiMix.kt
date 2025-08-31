package ponticello.ui.midi

import javafx.application.Platform
import ponticello.impl.*
import ponticello.model.flow.MixerFlow
import ponticello.model.flow.MixerFlow.MixerComponentMode.*
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.sc.client.SuperColliderClient
import reaktive.value.now
import javax.sound.midi.*

class AkaiMidiMix : Receiver {
    private var device: MidiDevice? = null
    var flow: MixerFlow? = null

    private val faderVolumes = Array(8) { zero }
    private val knobVolumes = Array(8) { zero }
    private val filterKnobs = Array(8) { zero }

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
        try {
            when (message.command) {
                ShortMessage.CONTROL_CHANGE -> {
                    val remainder = message.data1 % 4
                    val channel = (message.data1 - 16) / 4
                    val component = flow.components.getOrNull(channel)
                    when {
                        message.data1 == 62 -> {
                            Platform.runLater {
                                flow.masterVolume.now = getFaderVolume(message.data2, factor = 84.0)
                            }
                        }

                        remainder == 0 -> {
                            if (component == null) return
                            filterKnobs[channel] = getPan(message.data2).withPrecision(2) / 100
                            flow.context[SuperColliderClient].run(
                                "${flow.superColliderName}.setn(\\filters, ${filterKnobs.take(flow.components.size)})"
                            )
                        }

                        remainder == 1 -> {
                            val panVar = component?.pan ?: return
                            Platform.runLater {
                                panVar.now = getPan(message.data2)
                            }
                        }

                        remainder == 2 -> {
                            val volumeVar = component?.volume ?: return
                            knobVolumes[channel] = getKnobVolume(message.data2)
                            Platform.runLater {
                                volumeVar.now = knobVolumes[channel] + faderVolumes[channel]
                            }
                        }


                        remainder == 3 -> {
                            val volumeVar = component?.volume ?: return
                            val volume = getFaderVolume(message.data2)
                            faderVolumes[channel] = volume
                            Platform.runLater {
                                volumeVar.now = volume + knobVolumes[channel]
                            }
                        }
                    }
                }

                ShortMessage.NOTE_ON -> {
                    val action = (message.data1 - 1) % 3
                    val channel = (message.data1 - 1) / 3
                    val component = flow.components.getOrNull(channel) ?: return
                    when (action) {
                        0 -> component.state.now = if (component.state.now == Mute) Regular else Mute
                        1 -> component.state.now = if (component.state.now == Solo) Regular else Solo
                        2 -> flow.context.project[UI_STATE].selectedAudioBus = component.sourceBus
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(
                "Exception while processing MIDI message: " +
                        "${message.message.asList()} from device ${device?.deviceInfo?.name}",
                e
            )
        }
    }

    private fun getFaderVolume(byte: Int, factor: Double = 60.0): Decimal {
        val scaled = byte / 127.0 * factor
        return (scaled - 60.0).withPrecision(1)
    }

    private fun getKnobVolume(byte: Int): Decimal {
        val scaled = byte / 127.0 * 48.0
        return (scaled - 24.0).withPrecision(1)
    }

    private fun getPan(byte: Int): Decimal {
        val scaled = byte / 127.0 * 200
        return (scaled - 100.0).withPrecision(0)
    }

    override fun close() {
    }
}