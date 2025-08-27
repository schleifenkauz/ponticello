package ponticello.ui.score

import fxutils.infiniteSpace
import fxutils.prompt.SearchableListView
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
) : SearchableListView<InstrumentReference>("Select instrument") {
    override fun options(): List<InstrumentReference> = InstrumentReference.getOptions(context.project, midi)

    override fun createCell(option: InstrumentReference): Region {
        val (type, name) = when (option) {
            is InstrumentReference.UserDefined -> Pair("SynthDef", option.reference.getName())
            is InstrumentReference.VST -> {
                val flow = option.flow.force()
                Pair("VST: ${flow.pluginName.now}", flow.name.now)
            }

            InstrumentReference.None -> throw AssertionError()
        }
        return HBox(Label(name), infiniteSpace(), Label(type))
    }

    override fun extractText(option: InstrumentReference): String = option.getName()
}