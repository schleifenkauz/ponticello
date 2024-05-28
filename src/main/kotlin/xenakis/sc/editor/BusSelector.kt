package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.SimpleChoiceEditor
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import xenakis.sc.Bus
import xenakis.sc.view.BusSelectorControl
import xenakis.ui.XenakisController

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BusSelector(context: Context, bus: Bus = Bus.output) : SimpleChoiceEditor<Bus>(context, bus) {
    override fun choices(): List<Bus> {
        val project = context[XenakisController.currentProject]
        val bussesFromFlowGraph = project.flowGraph.busses.map { obj -> obj.bus }
        val globalControlBusses = project.globalControls.busses
        return globalControlBusses + bussesFromFlowGraph + BusSelectorControl.createNew
    }

    override fun toString(choice: Bus): String = when {
        choice.name == "<create-new>" -> "Create new"
        else -> "${choice.name} (${choice.rate})"
    }
}