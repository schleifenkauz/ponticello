package ponticello.ui.flow

import fxutils.infiniteSpace
import fxutils.prompt.SelectorPrompt
import fxutils.setFixedWidth
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference

class InstrumentSelectorPopup(
    private val context: Context,
) : SelectorPrompt<ObjectReference<InstrumentObject>>("Select instrument") {
    override fun options(): List<ObjectReference<InstrumentObject>> = context[InstrumentRegistry].map { it.reference() }

    override fun createCell(option: ObjectReference<InstrumentObject>): Region {
        val type = option.get()?.instrumentType ?: "???"
        val typeLabel = Label(type).setFixedWidth(70.0).styleClass("instrument-type-label")
        return HBox(Label(option.getName()).setFixedWidth(150.0), infiniteSpace(), typeLabel)
    }

    override fun extractText(option: ObjectReference<InstrumentObject>): String = option.getName()
}