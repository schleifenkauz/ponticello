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
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter
import xenakis.sc.send

@Serializable
@SerialName("BusValue")
class BusValueControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusValueControl(bus.copy())

    override fun providesConstantSynthArgument(): Boolean = false

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is NumericalControlSpec) {
            Logger.error("Expected NumericalControlSpec but got $spec")
            return false
        }
        return checkResolution(bus.now, "Bus")
    }

    override fun generateCodeFor(obj: ParameterizedObject, spec: ControlSpec): ScExpr =
        when (obj.def) {
            is ProcessDefObject -> throw AssertionError("BusValueControl is not implemented on process objects")
            else -> bus.now.force().superColliderExpr.send("getSynchronous")
        }

    override fun ScWriter.applyToSynth(
        parameter: String,
        spec: ControlSpec,
        obj: ParameterizedObject,
        synthVar: String,
    ) {
        val bus = bus.now.force().superColliderName
        +"${synthVar}.map(\\$parameter, $bus)"
    }
}