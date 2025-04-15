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
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.*
import xenakis.sc.client.ScWriter

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

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
    ): ScExpr {
        val busExpr = bus.now.force().superColliderExpr
        return when (obj.def) {
            is SynthDefObject -> busExpr.send("kr")
            is ProcessDefObject -> lambda { busExpr.send("getSynchronous") }
            else -> busExpr.send("getSynchronous")
        }
    }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject,
        synthVar: String,
        parameter: String,
        spec: ControlSpec
    ) {
        val bus = bus.now.force().superColliderName
        +"${synthVar}.map(\\$parameter, $bus)"
    }
}