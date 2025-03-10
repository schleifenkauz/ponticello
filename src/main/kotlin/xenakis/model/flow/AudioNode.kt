package xenakis.model.flow

import reaktive.value.ReactiveString
import xenakis.model.obj.BusObject
import xenakis.sc.client.ScWriter

sealed interface AudioNode {
    val superColliderName: ReactiveString

    fun getInputs(): Collection<BusObject>

    fun getOutputs(): Collection<BusObject>

    fun ScWriter.writeCode(placement: NodePlacement)

    fun addListener(listener: Listener)

    interface Listener {
        fun addedBus(bus: BusObject, type: FlowType)

        fun removedBus(bus: BusObject, type: FlowType)
    }
}