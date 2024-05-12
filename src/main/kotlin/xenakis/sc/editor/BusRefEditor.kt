package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.SimpleChoiceEditor
import xenakis.sc.Bus
import xenakis.ui.XenakisController

class BusRefEditor(context: Context, bus: Bus = Bus.output) : SimpleChoiceEditor<Bus>(context, bus) {
    override fun choices(): List<Bus> =
        context[XenakisController.currentProject].flowGraph.busses.map { obj -> obj.bus }

    override fun toString(choice: Bus): String = when {
        choice.name == "<create-new>" -> "Create new"
        else -> "${choice.name} (${choice.rate})"
    }
}