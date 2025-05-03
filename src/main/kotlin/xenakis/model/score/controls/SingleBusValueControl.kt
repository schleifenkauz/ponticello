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
import xenakis.model.registry.BusRegistry
import xenakis.sc.*
import xenakis.sc.client.ScWriter

@Serializable
@SerialName("SingleBusValue")
data class SingleBusValueControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context, parentObject: ParameterizedObject) {
        super.initialize(context, parentObject)
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

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        context: CodegenContext,
    ) {
        if (context == CodegenContext.Process) {
            val busName = bus.now.force().superColliderName
            +"${uniqueArgumentName(uniqueName, parameter)} = $busName"
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
        context: CodegenContext,
    ): ScExpr =
        when(context)  {
            CodegenContext.Process -> lambda("t") {
                val busVar = Identifier(uniqueArgumentName(uniqueName, parameter))
                busVar.send("getSynchronous")
            }

            else -> bus.now.force().superColliderExpr.send("getSynchronous")
        }
}