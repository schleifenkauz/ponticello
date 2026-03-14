package ponticello.ui.score

import fxutils.infiniteSpace
import fxutils.label
import fxutils.prompt.SelectorPrompt
import fxutils.setFixedWidth
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.instr.MidiEffectInstrument
import ponticello.model.obj.InstrumentReference
import ponticello.model.obj.project
import ponticello.model.project.instruments
import ponticello.model.registry.reference

class InstrumentSelectorPopup(
    private val context: Context,
) : SelectorPrompt<InstrumentReference>("Select instrument") {
    override fun options(): List<InstrumentReference> = context.project.instruments
        .filterNot { it is MidiEffectInstrument }
        .map { it.reference() }

    override fun createCell(option: InstrumentReference): Region {
        val type = option.get()?.instrumentType ?: "???"
        val typeLabel = Label(type).setFixedWidth(70.0).styleClass("instrument-type-label")
        return HBox(label(option.name).setFixedWidth(150.0), infiniteSpace(), typeLabel)
    }

    override fun extractText(option: InstrumentReference): String = option.getName()
}