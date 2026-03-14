package ponticello.ui.midi

import fxutils.prompt.SimpleSelectorPrompt
import hextant.context.Context
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.instr.MidiEffectInstrument
import ponticello.model.obj.MidiEffectInstrumentReference
import ponticello.model.registry.reference

class MidiEffectSelectorPrompt(private val context: Context) :
    SimpleSelectorPrompt<MidiEffectInstrumentReference>("Select MIDI Effect") {
    override fun extractText(option: MidiEffectInstrumentReference): String = option.getName()

    override fun options(): List<MidiEffectInstrumentReference> =
        context[InstrumentRegistry].filterIsInstance<MidiEffectInstrument>().map { it.reference() }
}