package xenakis.model.flow

import reaktive.value.ReactiveString
import xenakis.model.obj.BusObject
import xenakis.sc.client.ScWriter

sealed interface AudioNode {
    val superColliderName: ReactiveString

    fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject>

    fun ScWriter.writeCode(placement: NodePlacement)
}