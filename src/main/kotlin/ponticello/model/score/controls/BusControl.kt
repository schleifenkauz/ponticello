package ponticello.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.model.instr.BusObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.BusReference
import ponticello.model.obj.superColliderExpr
import ponticello.model.obj.superColliderName
import ponticello.model.registry.reference
import ponticello.model.server.BusRegistry
import ponticello.sc.BusControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.Identifier
import ponticello.sc.ScExpr
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Bus")
class BusControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context, namedControl: ParameterControlList.NamedParameterControl) {
        super.initialize(context, namedControl)
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusControl(bus.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is BusControlSpec) {
            Logger.warn("Expected BusControlSpec but got $spec", Logger.Category.Playback)
            return false
        }
        return checkResolution(obj, bus.now, "Bus")
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
    ): ScExpr = when(context) {
        CodegenContext.Synth, CodegenContext.SubArg -> bus.now.force().superColliderExpr
        CodegenContext.Process -> Identifier(uniqueArgumentName(uniqueName, parameter))
    }

    companion object {
        fun create(bus: BusObject) = BusControl(reactiveVariable(bus.reference()))
    }
}