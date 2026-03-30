package ponticello.ui.midi

import fxutils.prompt.SelectorPrompt
import hextant.context.Context
import ponticello.model.midi.MidiDeviceSpec

class MidiDeviceSelectorPrompt(
    private val type: MidiDeviceSpec.Type,
    private val context: Context
) : SelectorPrompt<MidiDeviceSpec>("Select ${type.name.lowercase()} device") {
    override fun options(): List<MidiDeviceSpec> =
        listOf(MidiDeviceSpec.None) + MidiDeviceSpec.getOptions(type, context)

    override fun displayText(option: MidiDeviceSpec): String = when (option) {
        is MidiDeviceSpec.ByName -> option.name
        MidiDeviceSpec.None -> "<none>"
    }

    override fun extractText(option: MidiDeviceSpec): String = when (option) {
        is MidiDeviceSpec.ByName -> option.name
        MidiDeviceSpec.None -> ""
    }
}