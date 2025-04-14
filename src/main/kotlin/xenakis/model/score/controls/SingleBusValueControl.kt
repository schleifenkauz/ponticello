package xenakis.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.model.obj.BusReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.*

@Serializable
@SerialName("SingleBusValue")
data class SingleBusValueControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = SingleBusValueControl(bus.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is NumericalControlSpec) {
            Logger.error("Expected NumericalControlSpec but got $spec")
            return false
        }
        return checkResolution(bus.now, "Bus")
    }

    override fun generateCodeFor(obj: ParameterizedObject, spec: ControlSpec): ScExpr =
        when (obj.def) {
            is ProcessDefObject -> lambda("t") { this.bus.now.force().superColliderExpr.send("getSynchronous") }
            else -> bus.now.force().superColliderExpr.send("getSynchronous")
        }
}