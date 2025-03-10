package xenakis.model.flow

import reaktive.value.ReactiveString
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.sc.client.ScWriter

class FlowGroup(
    val associatedBus: BusObject,
    private val flows: AudioFlows
) : AudioNode {
    override val superColliderName: ReactiveString = associatedBus.name.map { name -> "~flows_${name}" }

    override fun ScWriter.writeCode(placement: NodePlacement) {
        +"${superColliderName.now} = Group.new(${placement.target}, ${placement.addAction})"
    }

    override fun getInputs(): Collection<BusObject> = setOf(associatedBus)

    override fun getOutputs(): Collection<BusObject> = setOf(associatedBus)

    override fun addListener(listener: AudioNode.Listener) {
    }

    fun flows() = flows.associatedFlows(associatedBus)

    override fun toString(): String = superColliderName.now
}