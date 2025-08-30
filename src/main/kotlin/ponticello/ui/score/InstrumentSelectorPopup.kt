package ponticello.ui.score

import fxutils.infiniteSpace
import fxutils.prompt.SelectorPrompt
import fxutils.setFixedWidth
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.obj.InstrumentReference
import ponticello.model.obj.project
import reaktive.value.now

class InstrumentSelectorPopup(
    private val context: Context,
    private val midi: Boolean = false
) : SelectorPrompt<InstrumentReference>("Select instrument") {
    override fun options(): List<InstrumentReference> = InstrumentReference.getOptions(context.project, midi)

    override fun createCell(option: InstrumentReference): Region {
        val (type, name) = when (option) {
            is InstrumentReference.UserDefined -> Pair("SynthDef", option.reference.getName())
            is InstrumentReference.VST -> {
                val flow = option.flow.force()
                Pair("VST", flow.name.now)
            }

            InstrumentReference.None -> throw AssertionError()
        }
        val typeLabel = Label(type).setFixedWidth(70.0).styleClass("instrument-type-label")
        return HBox(Label(name).setFixedWidth(150.0), infiniteSpace(), typeLabel)
    }

    override fun extractText(option: InstrumentReference): String = option.getName()
}