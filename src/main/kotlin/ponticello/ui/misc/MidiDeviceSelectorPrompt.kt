package ponticello.ui.misc

import fxutils.prompt.SelectorPrompt
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiSystem

class MidiDeviceSelectorPrompt : SelectorPrompt<MidiDeviceSelectorPrompt.Option>("Select MIDI device") {
    override fun options(): List<Option> {
        val inputDevices = MidiSystem.getMidiDeviceInfo()
            .filter { info -> info.javaClass.simpleName.startsWith("MidiIn") }
            .map(Option::Device)
        return listOf(Option.NoDevice) + inputDevices
    }

    override fun displayText(option: Option): String = when (option) {
        is Option.Device -> option.info.name
        Option.NoDevice -> "<none>"
    }

    override fun extractText(option: Option): String = when (option) {
        is Option.Device -> option.info.name
        Option.NoDevice -> ""
    }

    sealed interface Option {
        data object NoDevice : Option
        data class Device(val info: MidiDevice.Info) : Option
    }
}