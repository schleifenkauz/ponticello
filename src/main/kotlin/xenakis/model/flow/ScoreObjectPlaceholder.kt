package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.BusObject
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectReference
import xenakis.sc.client.ScWriter

@Serializable
class ScoreObjectPlaceholder(val group: ObjectReference) : AudioFlow() {
    override val canDeactivate: Boolean
        get() = false

    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context, bus)
        group.resolve(context[GroupRegistry])
    }

    override fun copy(): AudioFlow = ScoreObjectPlaceholder(group)

    override fun ScWriter.writeCode(placement: NodePlacement) {
        +"$superColliderName = Group.new(${placement.target}, ${placement.addAction})"
    }

    override fun getDefaultName(): String = "Group ${group.getName()}"

    override fun getInputs(): Collection<BusObject> = emptySet()

    override fun getOutputs(): Collection<BusObject> = emptySet()

    override fun addListener(listener: AudioNode.Listener) {

    }
}