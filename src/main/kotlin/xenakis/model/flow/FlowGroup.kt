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

    override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> = buildSet {
        if (FlowType.In in flowType) add(associatedBus)
        if (FlowType.Out in flowType) add(associatedBus)
    }

    fun flows() = flows.associatedFlows(associatedBus)

    override fun toString(): String = superColliderName.now
}