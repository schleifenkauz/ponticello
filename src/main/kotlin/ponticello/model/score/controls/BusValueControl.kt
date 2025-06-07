package ponticello.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.model.obj.BusReference
import ponticello.model.obj.ParameterizedObject
import ponticello.model.registry.BusRegistry
import ponticello.model.score.ParameterControlList
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
@SerialName("BusValue")
class BusValueControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context, namedControl: ParameterControlList.NamedParameterControl) {
        super.initialize(context, namedControl)
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusValueControl(bus.copy())

    override fun providesConstantSynthArgument(obj: ParameterizedObject, spec: ControlSpec, cutoff: Decimal): Boolean = false

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
        cutoff: Decimal,
        ctx: CodegenContext,
    ) {
        if (ctx == CodegenContext.Process) {
            val busName = bus.now.force().superColliderName
            +"${uniqueArgumentName(uniqueName, parameter)} = $busName"
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, cutoff: Decimal, context: CodegenContext,
    ): ScExpr {
        val busExpr = bus.now.force().superColliderExpr
        return when(context) {
            CodegenContext.Process -> lambda {
                val busVar = Identifier(uniqueArgumentName(uniqueName, parameter))
                busVar.send("getSynchronous")
            }
            else -> busExpr.send("kr")
        }
    }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject,
        uniqueName: String,
        synthVar: String,
        parameter: String,
        spec: ControlSpec,
    ) {
        val bus = bus.now.force().superColliderName
        +"${synthVar}.map(\\$parameter, $bus)"
    }
}