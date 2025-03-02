package xenakis.model.flow

import xenakis.model.obj.BusObject

interface Flow {
    fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject>
}