package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.SimpleChoiceEditor
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.BusObject
import xenakis.model.BusRegistry
import xenakis.sc.Rate
import xenakis.sc.view.BusSelectorControl
import xenakis.ui.XenakisController

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BusSelector(
    context: Context,
    val preferredRate: Rate? = null,
    val preferredChannels: Int = -1,
    bus: BusObject = context[BusRegistry].filter(preferredRate, preferredChannels).firstOrNull()
        ?: context[BusRegistry].getOutputBus()
) : SimpleChoiceEditor<BusObject>(context, bus) {
    override fun choices(): List<BusObject> {
        val registry = context[XenakisController.currentProject].busses
        return registry.filter(preferredRate, preferredChannels) + BusSelectorControl.createNew
    }

    override fun toString(choice: BusObject): String = when {
        choice.name.now == "<create-new>" -> "Create new"
        else -> "${choice.name.now} (${choice.rate.now})"
    }
}