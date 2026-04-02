package ponticello.ui.midi

import javafx.application.Platform
import ponticello.impl.Decimal
import ponticello.impl.div
import ponticello.impl.withPrecision
import ponticello.impl.zero
import ponticello.model.flow.MixerFlow
import ponticello.model.flow.MixerFlow.MixerComponentMode.*
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.sc.client.SuperColliderClient
import reaktive.value.now

class AkaiMidiMix : OSCMidiListener() {
    var connectedFlow: MixerFlow? = null

    private val faderVolumes = Array(8) { zero }
    private val knobVolumes = Array(8) { zero }
    private val filterKnobs = Array(8) { zero }

    override fun noteOn(num: Int, velocity: Int) {
        val flow = connectedFlow ?: return
        val action = (num - 1) % 3
        val channel = (num - 1) / 3
        val component = flow.components.getOrNull(channel) ?: return
        when (action) {
            0 -> component.state.now = if (component.state.now == Mute) Regular else Mute
            1 -> component.state.now = if (component.state.now == Solo) Regular else Solo
            2 -> flow.context.project[PLAYBACK_SETTINGS].djMode.selectedBus = component.sourceBus.now
        }
        return
    }

    override fun controlChange(index: Int, value: Int) {
        val flow = connectedFlow ?: return
        val remainder = index % 4
        val channel = (index - 16) / 4
        val component = flow.components.getOrNull(channel)
        when {
            index == 62 -> {
                Platform.runLater {
                    flow.masterVolume.now = getFaderVolume(value, factor = 84.0)
                }
            }

            remainder == 0 -> {
                if (component == null) return
                filterKnobs[channel] = getPan(value).withPrecision(2) / 100
                flow.context[SuperColliderClient].run(
                    "${flow.superColliderName}.setn(\\filters, ${filterKnobs.take(flow.components.size)})"
                )
            }

            remainder == 1 -> {
                val panVar = component?.pan ?: return
                Platform.runLater {
                    panVar.now = getPan(value)
                }
            }

            remainder == 2 -> {
                val volumeVar = component?.volume ?: return
                knobVolumes[channel] = getKnobVolume(value)
                Platform.runLater {
                    volumeVar.now = knobVolumes[channel] + faderVolumes[channel]
                }
            }

            remainder == 3 -> {
                val volumeVar = component?.volume ?: return
                val volume = getFaderVolume(value)
                faderVolumes[channel] = volume
                Platform.runLater {
                    volumeVar.now = volume + knobVolumes[channel]
                }
            }
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
}