package ponticello.ui.score

import fxutils.infiniteSpace
import fxutils.prompt.SearchableListView
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.obj.MidiInstrument
import ponticello.model.obj.project
import reaktive.value.now

class MidiInstrumentSelectorPopup(
    private val context: Context,
) : SearchableListView<MidiInstrument>("Select instrument") {
    override fun options(): List<MidiInstrument> = MidiInstrument.getOptions(context.project)

    override fun createCell(option: MidiInstrument): Region {
        val (type, name) = when (option) {
            is MidiInstrument.SynthDef -> Pair("SynthDef", option.reference.getName())
            is MidiInstrument.VST -> {
                val flow = option.flow.force()
                Pair("VST: ${flow.pluginName}", flow.name.now)
            }

            MidiInstrument.None -> throw AssertionError()
        }
        return HBox(Label(name), infiniteSpace(), Label(type))
    }

    override fun extractText(option: MidiInstrument): String = when (option) {
        is MidiInstrument.SynthDef -> option.reference.getName()
        is MidiInstrument.VST -> option.flow.getName()
        MidiInstrument.None -> throw AssertionError()
    }
}