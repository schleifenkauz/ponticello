package xenakis.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.model.obj.BusObject
import xenakis.model.obj.BusReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.reference
import xenakis.sc.BusControlSpec
import xenakis.sc.ControlSpec
import xenakis.sc.ScExpr

@Serializable
@SerialName("Bus")
class BusControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusControl(bus.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is BusControlSpec) {
            Logger.error("Expected BusControlSpec but got $spec")
            return false
        }
        return checkResolution(bus.now, "Bus")
    }

    override fun generateCodeFor(obj: ParameterizedObject, spec: ControlSpec): ScExpr =
        bus.now.force().superColliderExpr

    companion object {
        fun create(bus: BusObject) = BusControl(reactiveVariable(bus.reference()))
    }
}