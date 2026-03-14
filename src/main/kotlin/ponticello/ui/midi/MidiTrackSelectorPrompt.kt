package ponticello.ui.midi

import fxutils.prompt.SelectorPrompt
import hextant.context.Context
import ponticello.model.flow.AudioFlows
import ponticello.model.obj.MidiTrackReference
import ponticello.model.registry.reference

class MidiTrackSelectorPrompt(private val context: Context) : SelectorPrompt<MidiTrackReference>("Select MIDI track") {
    override fun extractText(option: MidiTrackReference): String = option.getName()

    override fun options(): List<MidiTrackReference> = context[AudioFlows].allMidiTracks().map { it.reference() }
}